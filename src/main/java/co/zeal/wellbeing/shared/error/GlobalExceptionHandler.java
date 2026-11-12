package co.zeal.wellbeing.shared.error;

import co.zeal.wellbeing.shared.audit.DeniedAccessAudit;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce cualquier excepción a un {@link ApiError} con el código HTTP correcto. Ningún detalle
 * interno (SQL, stack traces) llega al cliente; los errores inesperados se registran en el log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String NOT_FOUND_MESSAGE = "El recurso solicitado no existe.";
    static final String CONTENTION_MESSAGE =
            "Hay muchas solicitudes sobre este recurso al mismo tiempo. Intenta de nuevo en un momento.";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiErrorFactory errors;
    private final DeniedAccessAudit deniedAccess;

    private final MeterRegistry meters;

    public GlobalExceptionHandler(ApiErrorFactory errors, DeniedAccessAudit deniedAccess, MeterRegistry meters) {
        this.meters = meters;
        this.errors = errors;
        this.deniedAccess = deniedAccess;
    }

    /** SEG-3: el recurso no existe o es de otra persona/empresa (indistinguible a propósito). */
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        deniedAccess.record(request, "recurso inexistente o ajeno");
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessConflictException.class)
    ResponseEntity<ApiError> handleConflict(BusinessConflictException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ResponseEntity<ApiError> handleRuleViolation(BusinessRuleViolationException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ResourceGoneException.class)
    ResponseEntity<ApiError> handleGone(ResourceGoneException ex, HttpServletRequest request) {
        return respond(HttpStatus.GONE, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        deniedAccess.record(request, "rol sin acceso");
        return respond(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE, request); // SEG-3: 404, no 403
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("La petición tiene datos inválidos.");
        return respond(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("La petición tiene datos inválidos.");
        return respond(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no tiene el formato esperado.", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                    HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST,
                "Falta el parámetro obligatorio '" + ex.getParameterName() + "'.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST,
                "El parámetro '" + ex.getName() + "' no tiene un formato válido.", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                      HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "Esta operación no está disponible.", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "El recurso solicitado no existe.", request);
    }

    /** Lock timeout o deadlock bajo alta concurrencia: el cliente puede reintentar. */
    @ExceptionHandler({PessimisticLockingFailureException.class, OptimisticLockingFailureException.class})
    ResponseEntity<ApiError> handleLockContention(RuntimeException ex, HttpServletRequest request) {
        log.warn("Contención de bloqueo en {}: {}", request.getRequestURI(), ex.getMessage());
        return respond(HttpStatus.CONFLICT, CONTENTION_MESSAGE, request);
    }

    /** Última barrera de las restricciones de la base de datos (p. ej. índice único de reservas). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Restricción de integridad violada en {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return respond(HttpStatus.CONFLICT, "La operación entra en conflicto con el estado actual. Intenta de nuevo.",
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        // R2-30: conteo de excepciones no controladas (etiqueta acotada: el tipo, nunca el mensaje).
        meters.counter("zeal.errors.unexpected", "exception", ex.getClass().getSimpleName()).increment();
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intenta de nuevo.", request);
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(errors.create(status, message, request));
    }
}

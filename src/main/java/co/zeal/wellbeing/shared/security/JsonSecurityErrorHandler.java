package co.zeal.wellbeing.shared.security;

import co.zeal.wellbeing.shared.audit.DeniedAccessAudit;
import co.zeal.wellbeing.shared.error.ApiErrorFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Responde 401 con el mismo cuerpo JSON que el resto de errores de la API. Un acceso denegado por rol
 * responde 404 (SEG-3: no revela que el recurso existe) y queda en la auditoría.
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String UNAUTHORIZED_MESSAGE = "Tu sesión no es válida. Vuelve a iniciar sesión.";
    static final String NOT_FOUND_MESSAGE = "El recurso solicitado no existe.";

    private final ApiErrorFactory errors;
    private final ObjectMapper objectMapper;
    private final DeniedAccessAudit deniedAccess;

    public JsonSecurityErrorHandler(ApiErrorFactory errors, ObjectMapper objectMapper, DeniedAccessAudit deniedAccess) {
        this.errors = errors;
        this.objectMapper = objectMapper;
        this.deniedAccess = deniedAccess;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, request, HttpStatus.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        deniedAccess.record(request, "rol sin acceso");
        write(response, request, HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
    }

    private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), errors.create(status, message, request));
    }
}

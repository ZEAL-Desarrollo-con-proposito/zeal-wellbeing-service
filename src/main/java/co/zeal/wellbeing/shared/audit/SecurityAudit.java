package co.zeal.wellbeing.shared.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * R2-14 · Auditoría de seguridad de solo inserción ({@code security_audit}) y la misma traza en el
 * log estructurado. Registra usuario (id interno: nunca correo ni nombre), IP, hora, tipo de evento,
 * resultado y correlationId.
 *
 * <p>Cada registro va en su propia transacción ({@code REQUIRES_NEW}): un intento denegado o un login
 * fallido queda auditado aunque la petición termine en error y su transacción se revierta.
 */
@Component
public class SecurityAudit {

    public enum Outcome { EXITO, FALLO, DENEGADO }

    private static final Logger log = LoggerFactory.getLogger("zeal.security.audit");
    private static final int MAX_DETAIL = 300;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate newTransaction;
    private final String service;

    public SecurityAudit(JdbcTemplate jdbc, PlatformTransactionManager transactions,
                         @Value("${spring.application.name}") String service) {
        this.jdbc = jdbc;
        this.newTransaction = new TransactionTemplate(transactions);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.service = service;
    }

    /** Evento originado en una petición HTTP de este servicio (toma IP y correlationId de ella). */
    public void record(String eventType, Outcome outcome, String userId, String detail) {
        HttpServletRequest request = currentRequest();
        String ip = request == null ? null : request.getRemoteAddr(); // IP real: forward-headers-strategy
        String correlationId = request == null ? MDC.get("correlationId") : firstNonBlank(
                request.getHeader("X-Request-Id"), MDC.get("correlationId"));
        record(service, eventType, outcome, userId, ip, correlationId, detail);
    }

    /** Evento con todos sus datos (p. ej. reenviado por otro servicio por Redis). */
    public void record(String origin, String eventType, Outcome outcome, String userId, String ip,
                       String correlationId, String detail) {
        String safeDetail = detail == null || detail.length() <= MAX_DETAIL ? detail : detail.substring(0, MAX_DETAIL);
        log.info("security_audit service={} event={} outcome={} userId={} ip={} correlationId={} detail=\"{}\"",
                origin, eventType, outcome, userId, ip, correlationId, safeDetail);
        try {
            newTransaction.executeWithoutResult(status -> jdbc.update(
                    "insert into security_audit (service, event_type, outcome, user_id, ip, correlation_id, detail) "
                            + "values (?, ?, ?, ?, ?, ?, ?)",
                    origin, eventType, outcome.name(), userId, ip, correlationId, safeDetail));
        } catch (RuntimeException e) {
            // La auditoría nunca tumba la operación; el fallo queda en el log (y en sus alertas).
            log.error("No se pudo escribir en security_audit: {}", e.getMessage());
        }
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}

package co.zeal.wellbeing.shared.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * SEG-3 · Registra en {@code security_audit} cada acceso denegado: un rol sin permiso, un recurso de
 * otra persona o empresa, o un id que no existe para quien lo pide (la consulta filtra por dueño, así
 * que "no existe" y "es ajeno" son indistinguibles a propósito). El cliente recibe 404 en todos los casos.
 */
@Component
public class DeniedAccessAudit {

    public static final String EVENT = "ACCESO_DENEGADO";

    private final SecurityAudit audit;

    public DeniedAccessAudit(SecurityAudit audit) {
        this.audit = audit;
    }

    public void record(HttpServletRequest request, String reason) {
        audit.record(EVENT, SecurityAudit.Outcome.DENEGADO, currentUserId(),
                request.getMethod() + " " + request.getRequestURI() + " · " + reason);
    }

    private static String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof Jwt jwt ? jwt.getSubject() : null;
    }
}

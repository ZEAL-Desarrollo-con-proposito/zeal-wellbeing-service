package co.zeal.wellbeing.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * R2-13 · Rechaza (401) los access tokens revocados, en el filtro de seguridad de cada petición.
 *
 * <p>Si Redis no responde se deja pasar el token (que igual vence en 15 min) y se registra el error:
 * negar todas las peticiones por una caída de Redis dejaría la plataforma entera sin servicio.
 */
public class RevocationValidator implements OAuth2TokenValidator<Jwt> {

    private static final Logger log = LoggerFactory.getLogger(RevocationValidator.class);
    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "La sesión fue revocada", null);

    private final TokenRevocations revocations;

    public RevocationValidator(TokenRevocations revocations) {
        this.revocations = revocations;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        try {
            return revocations.isRevoked(token) ? OAuth2TokenValidatorResult.failure(REVOKED)
                    : OAuth2TokenValidatorResult.success();
        } catch (DataAccessException e) {
            // TODO(ZEAL-REVIEW): fail-open ante caída de Redis (disponibilidad) vs fail-closed (seguridad).
            log.error("No se pudo consultar la lista negra de tokens: {}", e.getMessage());
            return OAuth2TokenValidatorResult.success();
        }
    }
}

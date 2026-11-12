package co.zeal.wellbeing.shared.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * R2-13 · Lista negra de tokens en Redis, compartida por los tres servicios y el gateway.
 *
 * <p>Se revoca por sesión ({@code sid}: la familia de refresh tokens, cubre todos los access tokens que
 * emitió) o por token ({@code jti}). Cada clave vive exactamente lo que le queda al token más largo que
 * cubre (TTL = vigencia restante): después ya no hace falta, el token venció solo.
 *
 * <p>Además se difunde por Redis Pub/Sub ({@value #CHANNEL}) para que el gateway cierre al instante las
 * conexiones STOMP abiertas con esos tokens (en todas sus réplicas).
 */
@Component
public class TokenRevocations {

    public static final String CHANNEL = "zeal.security.revocations";
    static final String SESSION_PREFIX = "zeal:revoked:sid:";
    static final String TOKEN_PREFIX = "zeal:revoked:jti:";
    private static final Logger log = LoggerFactory.getLogger(TokenRevocations.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public TokenRevocations(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void revokeSession(String sessionId, Duration remainingLifetime, String reason) {
        if (!remainingLifetime.isNegative() && !remainingLifetime.isZero()) {
            redis.opsForValue().set(SESSION_PREFIX + sessionId, reason, remainingLifetime);
        }
        broadcast(Map.of("sid", sessionId, "reason", reason));
    }

    public void revokeToken(String jti, Instant expiresAt, Instant now, String reason) {
        Duration remaining = Duration.between(now, expiresAt);
        if (!remaining.isNegative() && !remaining.isZero()) {
            redis.opsForValue().set(TOKEN_PREFIX + jti, reason, remaining);
        }
        broadcast(Map.of("jti", jti, "reason", reason));
    }

    /** ¿El token (o su sesión) fue revocado? Una sola ida a Redis. */
    public boolean isRevoked(Jwt jwt) {
        List<String> keys = new ArrayList<>(2);
        if (jwt.getId() != null) {
            keys.add(TOKEN_PREFIX + jwt.getId());
        }
        String sessionId = jwt.getClaimAsString(ZealClaims.SESSION);
        if (sessionId != null) {
            keys.add(SESSION_PREFIX + sessionId);
        }
        if (keys.isEmpty()) {
            return false;
        }
        Long found = redis.countExistingKeys(keys);
        return found != null && found > 0;
    }

    private void broadcast(Map<String, String> revocation) {
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(revocation));
        } catch (JsonProcessingException e) {
            log.error("No se pudo difundir la revocación", e);
        }
    }
}

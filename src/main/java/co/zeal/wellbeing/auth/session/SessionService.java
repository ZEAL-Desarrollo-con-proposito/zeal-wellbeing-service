package co.zeal.wellbeing.auth.session;

import co.zeal.wellbeing.auth.AuthProperties;
import co.zeal.wellbeing.auth.TokenService;
import co.zeal.wellbeing.auth.UserAccount;
import co.zeal.wellbeing.auth.UserAccountRepository;
import co.zeal.wellbeing.shared.audit.SecurityAudit;
import co.zeal.wellbeing.shared.id.Ids;
import co.zeal.wellbeing.shared.security.TokenRevocations;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * R2-12 · Sesiones de ZEAL con refresh token rotativo y detección de reutilización (SEG-4).
 *
 * <ul>
 *   <li>Cada inicio de sesión abre una familia; cada renovación usa el token una sola vez y entrega otro
 *   de la misma familia junto con un access token de 15 min.</li>
 *   <li>Rotación atómica entre réplicas: {@code UPDATE ... WHERE used_at IS NULL} (ver repositorio).</li>
 *   <li>Presentar un token ya usado significa que alguien más lo tiene: se revoca la familia completa en
 *   la base y en la lista negra de Redis (todas las réplicas y el gateway lo ven en el acto), se audita y
 *   se cuenta en la métrica que dispara la alerta (R2-15).</li>
 * </ul>
 */
@Service
public class SessionService {

    public static final String REUSE_METRIC = "zeal.security.refresh.reuse";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshFamilyRepository families;
    private final RefreshTokenRepository tokens;
    private final UserAccountRepository accounts;
    private final TokenService tokenService;
    private final TokenRevocations revocations;
    private final SecurityAudit audit;
    private final AuthProperties.Refresh settings;
    private final Clock clock;
    private final Counter reuseCounter;

    public SessionService(RefreshFamilyRepository families, RefreshTokenRepository tokens,
                          UserAccountRepository accounts, TokenService tokenService, TokenRevocations revocations,
                          SecurityAudit audit, AuthProperties properties, Clock clock, MeterRegistry meters) {
        this.families = families;
        this.tokens = tokens;
        this.accounts = accounts;
        this.tokenService = tokenService;
        this.revocations = revocations;
        this.audit = audit;
        this.settings = properties.refresh();
        this.clock = clock;
        this.reuseCounter = Counter.builder(REUSE_METRIC)
                .description("Sesiones revocadas por reutilización de un refresh token (SEG-4)")
                .register(meters);
    }

    /** Sesión recién emitida: access token, refresh token en claro (solo para la cookie) y su duración. */
    public record IssuedSession(TokenService.IssuedToken access, String refreshToken, Optional<Duration> cookieMaxAge,
                                UserAccount account) {
    }

    /** Resultado de presentar un refresh token. */
    public sealed interface Renewal permits Renewed, Rejected {
    }

    public record Renewed(IssuedSession session) implements Renewal {
    }

    /** {@code reuse}: el token ya se había usado y la sesión se revocó (SEG-4). */
    public record Rejected(boolean reuse) implements Renewal {
    }

    @Transactional
    public IssuedSession open(UserAccount account, boolean remember) {
        Instant now = Instant.now(clock);
        Duration lifetime = remember ? settings.rememberTtl() : settings.sessionTtl();
        RefreshFamily family = families.save(new RefreshFamily(Ids.next("sesion"), account.getId(), remember, now,
                now.plus(lifetime)));
        return issue(family, account, now);
    }

    @Transactional
    public Renewal renew(String rawToken) {
        Instant now = Instant.now(clock);
        Optional<RefreshToken> found = Optional.ofNullable(rawToken).filter(raw -> !raw.isBlank())
                .flatMap(raw -> tokens.findByTokenHash(hash(raw)));
        if (found.isEmpty()) {
            return new Rejected(false);
        }
        RefreshToken token = found.get();
        RefreshFamily family = families.findById(token.getFamilyId()).orElse(null);
        if (family == null || !family.isActiveAt(now) || !now.isBefore(token.getExpiresAt())) {
            return new Rejected(false);
        }
        if (token.getUsedAt() != null || tokens.markUsed(token.getId(), now) == 0) {
            Instant usedAt = token.getUsedAt() == null ? now : token.getUsedAt();
            boolean withinGrace = !settings.reuseGrace().isZero()
                    && Duration.between(usedAt, now).compareTo(settings.reuseGrace()) <= 0;
            if (withinGrace) {
                return new Rejected(false); // Dos pestañas a la vez: se tolera dentro del margen configurado.
            }
            revokeForReuse(family, now);
            return new Rejected(true);
        }
        return accounts.findById(family.getUserId())
                .<Renewal>map(account -> new Renewed(issue(family, account, now)))
                .orElseGet(() -> new Rejected(false));
    }

    /** Cierra la sesión del refresh token (logout). Idempotente. */
    @Transactional
    public Optional<String> close(String rawToken) {
        Instant now = Instant.now(clock);
        return Optional.ofNullable(rawToken).filter(raw -> !raw.isBlank())
                .flatMap(raw -> tokens.findByTokenHash(hash(raw)))
                .flatMap(token -> families.findById(token.getFamilyId()))
                .map(family -> {
                    family.revoke(now, "LOGOUT");
                    revocations.revokeSession(family.getId(), tokenService.timeToLive(), "LOGOUT");
                    return family.getUserId();
                });
    }

    private void revokeForReuse(RefreshFamily family, Instant now) {
        family.revoke(now, "REUTILIZACION");
        families.save(family);
        // Todos los access tokens de la familia caen en todas las réplicas (y el gateway cierra sus sockets).
        revocations.revokeSession(family.getId(), tokenService.timeToLive(), "REFRESH_REUTILIZADO");
        tokens.findByFamilyIdAndAccessExpiresAtAfter(family.getId(), now).forEach(issued ->
                revocations.revokeToken(issued.getAccessJti(), issued.getAccessExpiresAt(), now, "REFRESH_REUTILIZADO"));
        reuseCounter.increment();
        audit.record("REFRESH_REUTILIZADO", SecurityAudit.Outcome.DENEGADO, family.getUserId(),
                "sesion=" + family.getId() + " revocada en todas las réplicas");
    }

    private IssuedSession issue(RefreshFamily family, UserAccount account, Instant now) {
        TokenService.IssuedToken access = tokenService.issue(account, family.getId());
        String raw = randomToken();
        tokens.save(new RefreshToken(Ids.next("refresh"), family.getId(), hash(raw), now, family.getExpiresAt(),
                access.jti(), access.expiresAt()));
        Optional<Duration> maxAge = family.isRemember()
                ? Optional.of(Duration.between(now, family.getExpiresAt())) : Optional.empty();
        return new IssuedSession(access, raw, maxAge, account);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

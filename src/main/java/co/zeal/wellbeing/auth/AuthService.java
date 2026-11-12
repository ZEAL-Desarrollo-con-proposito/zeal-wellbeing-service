package co.zeal.wellbeing.auth;

import co.zeal.wellbeing.auth.oidc.LoginFlow;
import co.zeal.wellbeing.auth.oidc.LoginFlowCookie;
import co.zeal.wellbeing.auth.oidc.OidcLoginException;
import co.zeal.wellbeing.auth.oidc.OidcProvider;
import co.zeal.wellbeing.auth.oidc.ReturnPath;
import co.zeal.wellbeing.auth.session.SessionService;
import co.zeal.wellbeing.shared.audit.SecurityAudit;
import co.zeal.wellbeing.shared.error.BusinessRuleViolationException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * R2-17 · Login delegado en Microsoft (OIDC, código + PKCE; ZEAL es cliente confidencial, patrón BFF).
 * ZEAL no maneja contraseñas: Microsoft autentica y ZEAL solo admite las cuentas precargadas por cada
 * empresa (búsqueda por correo). Al terminar abre una sesión de ZEAL con refresh token rotativo.
 */
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OidcProvider provider;
    private final LoginFlowCookie flowCookie;
    private final UserAccountRepository accounts;
    private final SessionService sessions;
    private final SecurityAudit audit;
    private final MeterRegistry meters;

    public AuthService(OidcProvider provider, LoginFlowCookie flowCookie, UserAccountRepository accounts,
                       SessionService sessions, SecurityAudit audit, MeterRegistry meters) {
        this.meters = meters;
        this.provider = provider;
        this.flowCookie = flowCookie;
        this.accounts = accounts;
        this.sessions = sessions;
        this.audit = audit;
    }

    public record LoginStart(String authorizationUrl, String flowCookieValue) {
    }

    /** Resultado del regreso desde Microsoft: a qué ruta del SPA ir y, si entró, la sesión nueva. */
    public record LoginCompletion(String returnTo, SessionService.IssuedSession session, String errorCode) {

        boolean succeeded() {
            return session != null;
        }
    }

    public LoginStart startMicrosoftLogin(boolean remember, String returnTo) {
        String target = returnTo == null || returnTo.isBlank() ? ReturnPath.DEFAULT : returnTo;
        if (!ReturnPath.isInternal(target)) {
            audit.record("LOGIN_MICROSOFT", SecurityAudit.Outcome.DENEGADO, null, "ruta_de_regreso_externa");
            throw new BusinessRuleViolationException("La ruta de regreso debe ser una ruta interna de ZEAL.");
        }
        String verifier = random(48);
        LoginFlow flow = new LoginFlow(random(24), random(24), verifier, remember, target);
        String url = provider.authorizationUrl(flow.state(), flow.nonce(), challenge(verifier));
        return new LoginStart(url, flowCookie.encode(flow));
    }

    public LoginCompletion completeMicrosoftLogin(String code, String state, String providerError,
                                                  String flowCookieValue) {
        LoginFlow flow = flowCookie.decode(flowCookieValue).orElse(null);
        String returnTo = flow == null ? ReturnPath.DEFAULT : flow.returnTo();
        try {
            if (flow == null) {
                throw new OidcLoginException(OidcLoginException.FAILED, "flujo_ausente_o_vencido");
            }
            if (state == null || !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),
                    flow.state().getBytes(StandardCharsets.UTF_8))) {
                throw new OidcLoginException(OidcLoginException.FAILED, "state_invalido");
            }
            if (providerError != null) {
                // access_denied: la persona canceló en Microsoft (o no dio consentimiento).
                throw new OidcLoginException("access_denied".equals(providerError) ? OidcLoginException.CANCELLED
                        : OidcLoginException.FAILED, "proveedor:" + providerError);
            }
            if (code == null || code.isBlank()) {
                throw new OidcLoginException(OidcLoginException.FAILED, "sin_codigo");
            }
            Jwt idToken = provider.exchangeAndValidate(code, flow.codeVerifier(), flow.nonce());
            String email = emailOf(idToken);
            UserAccount account = accounts.findByEmail(email).orElseThrow(() ->
                    new OidcLoginException(OidcLoginException.NOT_REGISTERED, "cuenta_no_registrada"));
            SessionService.IssuedSession session = sessions.open(account, flow.remember());
            meters.counter("zeal.business.logins", "role", account.getRole().name()).increment(); // R2-31
            audit.record("LOGIN_MICROSOFT", SecurityAudit.Outcome.EXITO, account.getId(),
                    "sesion=" + session.access().jti());
            return new LoginCompletion(returnTo, session, null);
        } catch (OidcLoginException e) {
            audit.record("LOGIN_MICROSOFT", e.code().equals(OidcLoginException.CANCELLED)
                    ? SecurityAudit.Outcome.FALLO : SecurityAudit.Outcome.DENEGADO, null, e.reason());
            return new LoginCompletion(returnTo, null, e.code());
        }
    }

    /** Microsoft entrega el correo en {@code email} (claim opcional) o como {@code preferred_username}. */
    private static String emailOf(Jwt idToken) {
        String email = idToken.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = idToken.getClaimAsString("preferred_username");
        }
        if (email == null || email.isBlank()) {
            throw new OidcLoginException(OidcLoginException.NOT_REGISTERED, "id_token_sin_correo");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String random(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String challenge(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

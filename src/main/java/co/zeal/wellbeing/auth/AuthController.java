package co.zeal.wellbeing.auth;

import co.zeal.wellbeing.auth.oidc.LoginFlowCookie;
import co.zeal.wellbeing.auth.session.SessionService;
import co.zeal.wellbeing.shared.audit.SecurityAudit;
import co.zeal.wellbeing.shared.error.ApiError;
import co.zeal.wellbeing.shared.error.ApiErrorFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;

/**
 * Autenticación de ZEAL (HU-BE-22). Contrato para FE en docs/contratos/auth-microsoft.md.
 *
 * <ul>
 *   <li>{@code POST /api/auth/microsoft} → URL de autorización de Microsoft (+ cookie corta del flujo).</li>
 *   <li>{@code GET /api/auth/microsoft/callback} → regreso de Microsoft; deja la cookie de renovación y
 *   redirige a la ruta interna pedida o a {@code /login?error=cuenta_no_registrada|cancelado|fallo}.</li>
 *   <li>{@code POST /api/auth/refresh} → access token nuevo (15 min) y cookie rotada.</li>
 *   <li>{@code POST /api/auth/logout} → revoca la sesión.</li>
 * </ul>
 * {@code POST /api/auth/login} (contraseña) se retiró.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String REFRESH_COOKIE = "zeal_refresh";
    static final String REFRESH_PATH = "/api/auth";
    static final String SESSION_EXPIRED = "Tu sesión terminó. Vuelve a iniciar sesión.";

    private final AuthService auth;
    private final SessionService sessions;
    private final SecurityAudit audit;
    private final AuthProperties properties;
    private final ApiErrorFactory errors;

    public AuthController(AuthService auth, SessionService sessions, SecurityAudit audit, AuthProperties properties,
                          ApiErrorFactory errors) {
        this.auth = auth;
        this.sessions = sessions;
        this.audit = audit;
        this.properties = properties;
        this.errors = errors;
    }

    public record MicrosoftLoginRequest(boolean remember, String returnTo) {
    }

    public record MicrosoftLoginResponse(String authorizationUrl) {
    }

    @PostMapping("/microsoft")
    public ResponseEntity<MicrosoftLoginResponse> startMicrosoftLogin(@RequestBody(required = false)
                                                                      MicrosoftLoginRequest request) {
        MicrosoftLoginRequest body = request == null ? new MicrosoftLoginRequest(false, null) : request;
        AuthService.LoginStart start = auth.startMicrosoftLogin(body.remember(), body.returnTo());
        ResponseCookie flow = cookie(LoginFlowCookie.NAME, start.flowCookieValue(), LoginFlowCookie.PATH, "Lax")
                .maxAge(LoginFlowCookie.TTL).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, flow.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new MicrosoftLoginResponse(start.authorizationUrl()));
    }

    @GetMapping("/microsoft/callback")
    public ResponseEntity<Void> microsoftCallback(@RequestParam(required = false) String code,
                                                  @RequestParam(required = false) String state,
                                                  @RequestParam(required = false) String error,
                                                  @CookieValue(name = LoginFlowCookie.NAME, required = false) String flow) {
        AuthService.LoginCompletion completion = auth.completeMicrosoftLogin(code, state, error, flow);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, expired(LoginFlowCookie.NAME, LoginFlowCookie.PATH, "Lax"));
        headers.setCacheControl("no-store");
        String target;
        if (completion.succeeded()) {
            headers.add(HttpHeaders.SET_COOKIE, refreshCookie(completion.session()));
            target = frontend(completion.returnTo(), null);
        } else {
            target = frontend("/login", completion.errorCode());
        }
        headers.setLocation(URI.create(target));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                     HttpServletRequest request) {
        SessionService.Renewal renewal = sessions.renew(refreshToken);
        if (renewal instanceof SessionService.Renewed renewed) {
            SessionService.IssuedSession session = renewed.session();
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(session))
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(new LoginResponse(session.access().value(), "Bearer", session.access().expiresInSeconds(),
                            LoginResponse.UserView.of(session.account())));
        }
        ApiError body = errors.create(HttpStatus.UNAUTHORIZED, SESSION_EXPIRED, request);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, expired(REFRESH_COOKIE, REFRESH_PATH, "Strict"))
                .body(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        sessions.close(refreshToken).ifPresent(userId ->
                audit.record("LOGOUT", SecurityAudit.Outcome.EXITO, userId, null));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expired(REFRESH_COOKIE, REFRESH_PATH, "Strict"))
                .build();
    }

    /** Cookie de renovación: httpOnly, Secure, SameSite=Strict, solo para /api/auth. Sin "recordarme", de sesión. */
    private String refreshCookie(SessionService.IssuedSession session) {
        ResponseCookie.ResponseCookieBuilder builder = cookie(REFRESH_COOKIE, session.refreshToken(), REFRESH_PATH, "Strict");
        session.cookieMaxAge().ifPresent(builder::maxAge);
        return builder.build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String name, String value, String path, String sameSite) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(properties.cookie().secure()).path(path)
                .sameSite(sameSite);
    }

    private String expired(String name, String path, String sameSite) {
        return cookie(name, "", path, sameSite).maxAge(Duration.ZERO).build().toString();
    }

    /** La ruta ya viene validada como interna (ReturnPath) y los códigos de error son fijos. */
    private String frontend(String path, String errorCode) {
        String base = properties.frontendUrl().replaceAll("/+$", "");
        return errorCode == null ? base + path : base + path + "?error=" + errorCode;
    }

}

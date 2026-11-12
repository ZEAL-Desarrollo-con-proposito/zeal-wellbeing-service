package co.zeal.wellbeing.auth;

import co.zeal.wellbeing.auth.oidc.LoginFlow;
import co.zeal.wellbeing.auth.oidc.LoginFlowCookie;
import co.zeal.wellbeing.auth.oidc.OidcLoginException;
import co.zeal.wellbeing.auth.oidc.OidcProvider;
import co.zeal.wellbeing.support.IntegrationTest;
import co.zeal.wellbeing.support.MicrosoftBrowser;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R2-11 · SEG-2 con el proveedor OIDC de prueba (mock-oauth2-server en lugar de Microsoft Entra ID):
 * el 100 % de las cuentas registradas entra; se rechazan cuentas no registradas, ID tokens con firma,
 * emisor, audiencia o nonce inválidos, state alterado y rutas de regreso externas; el mensaje no
 * revela qué cuentas existen; responde en ≤ 1 s y cada intento queda en security_audit con IP y hora.
 */
@AutoConfigureMockMvc
class MicrosoftLoginIT extends IntegrationTest {

    private static final String CLIENT_ID = "zeal-local";

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    LoginFlowCookie flowCookie;
    @Autowired
    OidcProvider provider;

    @Test
    void everyRegisteredAccountSignsInAndLandsOnItsReturnPath() throws Exception {
        List<String> emails = jdbc.queryForList("select email from user_accounts", String.class);
        assertThat(emails).hasSizeGreaterThanOrEqualTo(10);
        for (String email : emails) {
            Login login = login(true, "/empleado/mis-citas", "{\"email\":\"" + email.toUpperCase() + "\"}");

            assertThat(login.location()).isEqualTo("https://zeal.test/empleado/mis-citas");
            assertThat(login.callbackMillis()).as("respuesta ≤ 1 s").isLessThanOrEqualTo(1000);
            Cookie refresh = login.refreshCookie();
            assertThat(refresh).as("cookie de renovación para %s", email).isNotNull();
            assertThat(refresh.isHttpOnly()).isTrue();
            assertThat(refresh.getSecure()).isTrue();
            assertThat(refresh.getPath()).isEqualTo("/api/auth");
            assertThat(refresh.getAttribute("SameSite")).isEqualTo("Strict");
            assertThat(refresh.getMaxAge()).as("recordarme: cookie persistente").isGreaterThan(29 * 24 * 3600);

            mvc.perform(post("/api/auth/refresh").cookie(refresh))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(900))
                    .andExpect(jsonPath("$.user.email").value(email));
        }
    }

    @Test
    void withoutRememberTheRefreshCookieLastsOnlyTheBrowserSession() throws Exception {
        Login login = login(false, "/", "{\"email\":\"david.cajamarca@nexura.co\"}");

        assertThat(login.location()).isEqualTo("https://zeal.test/");
        assertThat(login.refreshCookie().getMaxAge()).as("cookie de sesión: sin Max-Age").isEqualTo(-1);
    }

    @Test
    void unregisteredAccountIsRejectedWithAGenericMessageAndAudited() throws Exception {
        Login login = login(false, "/", "{\"email\":\"intruso@otra-empresa.co\"}", "203.0.113.7");

        assertThat(login.location()).isEqualTo("https://zeal.test/login?error=cuenta_no_registrada");
        assertThat(login.refreshCookie()).isNull();
        Map<String, Object> audit = jdbc.queryForMap("select * from security_audit where event_type = 'LOGIN_MICROSOFT'"
                + " and detail = 'cuenta_no_registrada' order by id desc limit 1");
        assertThat(audit).containsEntry("outcome", "DENEGADO").containsEntry("ip", "203.0.113.7")
                .containsEntry("correlation_id", "prueba-login-0001");
        assertThat(audit.get("occurred_at")).isNotNull();
        assertThat(audit.get("user_id")).as("nunca el correo").isNull();
    }

    @Test
    void wrongAudienceIsRejected() throws Exception {
        Login login = login(false, "/", "{\"email\":\"david.cajamarca@nexura.co\",\"aud\":\"otra-aplicacion\"}");
        assertThat(login.location()).isEqualTo("https://zeal.test/login?error=fallo");
        assertThat(login.refreshCookie()).isNull();
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        Login login = login(false, "/", "{\"email\":\"david.cajamarca@nexura.co\",\"iss\":\"https://evil.example\"}");
        assertThat(login.location()).isEqualTo("https://zeal.test/login?error=fallo");
    }

    @Test
    void alteredStateIsRejected() throws Exception {
        Start start = start(false, "/");
        Map<String, String> callback = MicrosoftBrowser.signIn(start.authorizationUrl(),
                "{\"email\":\"david.cajamarca@nexura.co\"}");

        MvcResult result = mvc.perform(get("/api/auth/microsoft/callback").param("code", callback.get("code"))
                        .param("state", "otro-state").cookie(start.flowCookie()))
                .andExpect(status().isFound()).andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("https://zeal.test/login?error=fallo");
    }

    @Test
    void nonceThatDoesNotMatchTheFlowIsRejected() throws Exception {
        Start start = start(false, "/");
        Map<String, String> callback = MicrosoftBrowser.signIn(start.authorizationUrl(),
                "{\"email\":\"david.cajamarca@nexura.co\"}");
        LoginFlow original = flowCookie.decode(start.flowCookie().getValue()).orElseThrow();
        Cookie forged = new Cookie(LoginFlowCookie.NAME, flowCookie.encode(new LoginFlow(original.state(),
                "nonce-de-otro-flujo", original.codeVerifier(), false, "/")));

        MvcResult result = mvc.perform(get("/api/auth/microsoft/callback").param("code", callback.get("code"))
                        .param("state", callback.get("state")).cookie(forged))
                .andExpect(status().isFound()).andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("https://zeal.test/login?error=fallo");
        assertThat(jdbc.queryForObject("select detail from security_audit where event_type = 'LOGIN_MICROSOFT'"
                + " order by id desc limit 1", String.class)).isEqualTo("nonce_invalido");
    }

    @Test
    void idTokenSignedByAnotherKeyIsRejected() throws Exception {
        // Token de otro emisor (otra clave) que dice venir del nuestro: la firma no verifica con nuestras JWKS.
        String forged = idTokenFrom("otro", "{\"iss\":\"" + oidcIssuer() + "\",\"aud\":\"" + CLIENT_ID + "\"}", "n-1");

        assertThatThrownBy(() -> provider.validate(forged, "n-1"))
                .isInstanceOf(OidcLoginException.class).hasMessage("id_token_invalido");
        assertThat(provider.validate(idTokenFrom("microsoft", "{\"aud\":\"" + CLIENT_ID + "\"}", "n-2"), "n-2")
                .getClaimAsString("nonce")).isEqualTo("n-2");
    }

    @Test
    void cancellingAtMicrosoftReturnsToLoginWithCancelled() throws Exception {
        Start start = start(false, "/");
        LoginFlow flow = flowCookie.decode(start.flowCookie().getValue()).orElseThrow();

        MvcResult result = mvc.perform(get("/api/auth/microsoft/callback").param("error", "access_denied")
                        .param("state", flow.state()).cookie(start.flowCookie()))
                .andExpect(status().isFound()).andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("https://zeal.test/login?error=cancelado");
    }

    @Test
    void callbackWithoutTheFlowCookieFails() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/microsoft/callback").param("code", "x").param("state", "y"))
                .andExpect(status().isFound()).andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("https://zeal.test/login?error=fallo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://evil.example/robar", "//evil.example", "/\\evil.example", "javascript:alert(1)",
            "/%5Cevil.example", "empleado/mis-citas"})
    void externalReturnPathsAreRejected(String returnTo) throws Exception {
        mvc.perform(post("/api/auth/microsoft").contentType("application/json")
                        .content("{\"remember\":false,\"returnTo\":\"" + returnTo.replace("\\", "\\\\") + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ----------------------------------------------------------------------------------------------

    record Start(String authorizationUrl, Cookie flowCookie) {
    }

    record Login(String location, Cookie refreshCookie, long callbackMillis) {
    }

    private Start start(boolean remember, String returnTo) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/microsoft").contentType("application/json")
                        .content("{\"remember\":" + remember + ",\"returnTo\":\"" + returnTo + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String url = JsonPath.read(result.getResponse().getContentAsString(), "$.authorizationUrl");
        assertThat(url).startsWith(oidcIssuer() + "/authorize").contains("code_challenge_method=S256")
                .contains("client_id=" + CLIENT_ID).contains("nonce=").contains("state=");
        Cookie flow = result.getResponse().getCookie(LoginFlowCookie.NAME);
        assertThat(flow).isNotNull();
        assertThat(flow.isHttpOnly()).isTrue();
        return new Start(url, flow);
    }

    private Login login(boolean remember, String returnTo, String claims) throws Exception {
        return login(remember, returnTo, claims, "198.51.100.20");
    }

    private Login login(boolean remember, String returnTo, String claims, String clientIp) throws Exception {
        Start start = start(remember, returnTo);
        Map<String, String> callback = MicrosoftBrowser.signIn(start.authorizationUrl(), claims);
        long begin = System.nanoTime();
        MvcResult result = mvc.perform(get("/api/auth/microsoft/callback").param("code", callback.get("code"))
                        .param("state", callback.get("state")).cookie(start.flowCookie())
                        .header("X-Request-Id", "prueba-login-0001")
                        .with(request -> {
                            request.setRemoteAddr(clientIp);
                            return request;
                        }))
                .andExpect(status().isFound())
                .andReturn();
        long millis = Duration.ofNanos(System.nanoTime() - begin).toMillis();
        return new Login(result.getResponse().getRedirectedUrl(), result.getResponse().getCookie("zeal_refresh"), millis);
    }

    /** Un ID token emitido directamente por el emisor {@code issuerId} del proveedor de prueba. */
    private static String idTokenFrom(String issuerId, String claims, String nonce) throws Exception {
        String base = "http://" + OIDC.getHost() + ":" + OIDC.getMappedPort(8080) + "/" + issuerId;
        String verifier = "v".repeat(50);
        String challenge = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        String authorize = base + "/authorize?client_id=" + CLIENT_ID + "&response_type=code&scope=openid"
                + "&redirect_uri=" + URLEncoder.encode("http://localhost/cb", StandardCharsets.UTF_8)
                + "&state=s&nonce=" + nonce + "&code_challenge=" + challenge + "&code_challenge_method=S256";
        String code = MicrosoftBrowser.signIn(authorize, claims).get("code");
        String form = "grant_type=authorization_code&code=" + code + "&client_id=" + CLIENT_ID
                + "&client_secret=x&code_verifier=" + verifier
                + "&redirect_uri=" + URLEncoder.encode("http://localhost/cb", StandardCharsets.UTF_8);
        HttpResponse<String> token = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base + "/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        return JsonPath.read(token.body(), "$.id_token");
    }
}

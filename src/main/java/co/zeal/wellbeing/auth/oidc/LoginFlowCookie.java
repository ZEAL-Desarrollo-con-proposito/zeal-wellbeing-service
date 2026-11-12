package co.zeal.wellbeing.auth.oidc;

import co.zeal.wellbeing.shared.security.JwtProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Cookie del flujo de login (state, nonce y verificador PKCE). Va firmada (HS256) con una clave derivada
 * del secreto de ZEAL para que nadie pueda fabricarla, es httpOnly (el JavaScript no la lee) y vence en
 * 10 minutos. {@code SameSite=Lax} a propósito: el regreso desde Microsoft es una navegación desde otro
 * sitio y una cookie {@code Strict} no viajaría en ella.
 */
@Component
public final class LoginFlowCookie {

    public static final String NAME = "zeal_oidc";
    public static final String PATH = "/api/auth/microsoft";
    public static final Duration TTL = Duration.ofMinutes(10);

    private final byte[] key;
    private final Clock clock;

    public LoginFlowCookie(JwtProperties jwt, Clock clock) {
        this.key = derive(jwt.secret());
        this.clock = clock;
    }

    public String encode(LoginFlow flow) {
        Instant now = Instant.now(clock);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("state", flow.state()).claim("nonce", flow.nonce()).claim("verifier", flow.codeVerifier())
                .claim("remember", flow.remember()).claim("returnTo", flow.returnTo())
                .expirationTime(Date.from(now.plus(TTL)))
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("No se pudo firmar el flujo de login", e);
        }
    }

    /** El flujo, si la cookie es auténtica y no venció. */
    public Optional<LoginFlow> decode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(value);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!jwt.verify(new MACVerifier(key)) || claims.getExpirationTime() == null
                    || claims.getExpirationTime().toInstant().isBefore(Instant.now(clock))) {
                return Optional.empty();
            }
            return Optional.of(new LoginFlow(claims.getStringClaim("state"), claims.getStringClaim("nonce"),
                    claims.getStringClaim("verifier"), Boolean.TRUE.equals(claims.getBooleanClaim("remember")),
                    ReturnPath.orDefault(claims.getStringClaim("returnTo"))));
        } catch (ParseException | JOSEException e) {
            return Optional.empty();
        }
    }

    private static byte[] derive(String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal("zeal-oidc-login-flow".getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}

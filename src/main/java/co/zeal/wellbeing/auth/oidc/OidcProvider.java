package co.zeal.wellbeing.auth.oidc;

import co.zeal.wellbeing.auth.AuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

/**
 * Cliente OIDC confidencial ante Microsoft Entra ID (o el proveedor de prueba en local): arma la URL de
 * autorización (código + PKCE S256), canjea el código por tokens con el secreto del cliente y valida el
 * ID token: firma con las JWKS del proveedor, emisor, audiencia (nuestro client id), vigencia y nonce.
 *
 * <p>Los metadatos del proveedor (descubrimiento) y el validador se calculan una vez: son configuración,
 * no estado de sesión.
 */
@Component
public class OidcProvider {

    private final AuthProperties.Oidc config;
    private final RestClient http;
    private volatile Metadata metadata;
    private volatile NimbusJwtDecoder idTokenDecoder;

    public OidcProvider(AuthProperties properties, RestClient.Builder builder) {
        this.config = properties.oidc();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder.requestFactory(factory).build();
    }

    public String authorizationUrl(String state, String nonce, String codeChallenge) {
        String endpoint = config.authorizationUri() == null || config.authorizationUri().isBlank()
                ? metadata().authorizationEndpoint() : config.authorizationUri();
        return UriComponentsBuilder.fromUriString(endpoint)
                .queryParam("client_id", config.clientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", config.redirectUri())
                .queryParam("response_mode", "query")
                .queryParam("scope", String.join(" ", config.scopes()))
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .encode()
                .build()
                .toUriString();
    }

    /** Canjea el código (con el verificador PKCE) y devuelve el ID token ya validado. */
    public Jwt exchangeAndValidate(String code, String codeVerifier, String expectedNonce) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", config.redirectUri());
        form.add("client_id", config.clientId());
        form.add("client_secret", config.clientSecret());
        form.add("code_verifier", codeVerifier);
        JsonNode response;
        try {
            response = http.post().uri(metadata().tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new OidcLoginException(OidcLoginException.FAILED, "canje_de_codigo_rechazado");
        }
        if (response == null || !response.hasNonNull("id_token")) {
            throw new OidcLoginException(OidcLoginException.FAILED, "respuesta_sin_id_token");
        }
        return validate(response.get("id_token").asText(), expectedNonce);
    }

    /** Firma (JWKS del proveedor), emisor, audiencia, vigencia y nonce del ID token. */
    public Jwt validate(String rawIdToken, String expectedNonce) {
        try {
            Jwt idToken = idTokenDecoder().decode(rawIdToken);
            if (!expectedNonce.equals(idToken.getClaimAsString("nonce"))) {
                throw new OidcLoginException(OidcLoginException.FAILED, "nonce_invalido");
            }
            return idToken;
        } catch (JwtException e) {
            throw new OidcLoginException(OidcLoginException.FAILED, "id_token_invalido");
        }
    }

    private NimbusJwtDecoder idTokenDecoder() {
        NimbusJwtDecoder decoder = idTokenDecoder;
        if (decoder == null) {
            decoder = NimbusJwtDecoder.withJwkSetUri(metadata().jwksUri())
                    .jwsAlgorithm(SignatureAlgorithm.RS256)
                    .build();
            OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null
                    && jwt.getAudience().contains(config.clientId())
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audiencia inválida", null));
            OAuth2TokenValidator<Jwt> expiry = jwt -> jwt.getClaims().containsKey(JwtClaimNames.EXP)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "sin vencimiento", null));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(config.issuerUri()), audience, expiry));
            idTokenDecoder = decoder;
        }
        return decoder;
    }

    private Metadata metadata() {
        Metadata current = metadata;
        if (current == null) {
            String discovery = config.issuerUri().replaceAll("/+$", "") + "/.well-known/openid-configuration";
            JsonNode document;
            try {
                document = http.get().uri(discovery).retrieve().body(JsonNode.class);
            } catch (RestClientException e) {
                throw new OidcLoginException(OidcLoginException.FAILED, "proveedor_no_disponible");
            }
            if (document == null || !document.hasNonNull("authorization_endpoint")
                    || !document.hasNonNull("token_endpoint") || !document.hasNonNull("jwks_uri")) {
                throw new OidcLoginException(OidcLoginException.FAILED, "descubrimiento_incompleto");
            }
            current = new Metadata(document.get("authorization_endpoint").asText(),
                    document.get("token_endpoint").asText(), document.get("jwks_uri").asText());
            metadata = current;
        }
        return current;
    }

    record Metadata(String authorizationEndpoint, String tokenEndpoint, String jwksUri) {
    }

}

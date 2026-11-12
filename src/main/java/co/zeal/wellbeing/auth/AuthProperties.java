package co.zeal.wellbeing.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/** Login con Microsoft y sesión de ZEAL (R2-17, R2-12). */
@Validated
@ConfigurationProperties("app.auth")
public record AuthProperties(@NotBlank String frontendUrl, @Valid @NotNull Refresh refresh,
                             @Valid @NotNull Cookie cookie, @Valid @NotNull Oidc oidc) {

    public record Refresh(@NotNull Duration rememberTtl, @NotNull Duration sessionTtl, @NotNull Duration reuseGrace) {
    }

    public record Cookie(boolean secure) {
    }

    /**
     * Proveedor OIDC (Microsoft Entra ID). {@code issuerUri} sirve para el descubrimiento y para validar
     * el {@code iss} del ID token; {@code authorizationUri} solo se fija si el navegador llega al
     * proveedor por una URL distinta de la del servidor (el mock local en Docker).
     */
    public record Oidc(@NotBlank String issuerUri, String authorizationUri, @NotBlank String clientId,
                       @NotBlank String clientSecret, @NotBlank String redirectUri, @NotNull List<String> scopes) {
    }
}

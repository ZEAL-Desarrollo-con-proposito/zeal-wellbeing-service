package co.zeal.wellbeing.shared.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Configuración del JWT compartido por los microservicios (HS256). */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "El secreto JWT debe tener al menos 256 bits") String secret,
        @NotBlank String issuer,
        @NotNull Duration timeToLive) {
}

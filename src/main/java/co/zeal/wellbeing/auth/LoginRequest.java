package co.zeal.wellbeing.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El correo y la contraseña son obligatorios") String email,
        @NotBlank(message = "El correo y la contraseña son obligatorios") String password) {
}

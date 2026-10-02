package co.zeal.wellbeing.auth;

import co.zeal.wellbeing.config.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    private static final String DEMO_PASSWORD = "Zeal2026*";

    private static final List<Map<String, String>> SEED_USERS = List.of(
            Map.of("id", "usr-empleado-001", "fullName", "David Cajamarca",
                   "email", "david.cajamarca@nexura.co", "role", "EMPLEADO",
                   "organization", "Nexura S.A.S."),
            Map.of("id", "usr-empleado-002", "fullName", "Mariana Torres",
                   "email", "mariana.torres@nexura.co", "role", "EMPLEADO",
                   "organization", "Nexura S.A.S."),
            Map.of("id", "usr-psicologo-001", "fullName", "Laura Martínez",
                   "email", "laura.martinez@zeal.co", "role", "PSICOLOGO",
                   "organization", "ZEAL"),
            Map.of("id", "usr-admin-001", "fullName", "Talento Humano",
                   "email", "talento.humano@nexura.co", "role", "ADMIN",
                   "organization", "Nexura S.A.S.")
    );

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        if (request.email() == null || request.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El correo y la contraseña son obligatorios");
        }
        if (!DEMO_PASSWORD.equals(request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
        }
        String normalizedEmail = request.email().trim().toLowerCase();
        Map<String, String> user = SEED_USERS.stream()
                .filter(u -> u.get("email").equals(normalizedEmail))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos"));

        long expiresIn = 86400L;
        String token = jwtService.generate(
                user.get("id"), user.get("role"), user.get("email"),
                user.get("fullName"), user.get("organization"));

        return new LoginResponse(
                token,
                "Bearer",
                expiresIn,
                new UserDto(user.get("id"), user.get("fullName"), user.get("email"),
                            user.get("role"), user.get("organization"))
        );
    }

    public record LoginRequest(String email, String password) {}
    public record UserDto(String id, String fullName, String email, String role, String organization) {}
    public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserDto user) {}
}

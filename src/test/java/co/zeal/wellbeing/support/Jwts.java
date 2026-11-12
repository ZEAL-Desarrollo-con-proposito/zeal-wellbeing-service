package co.zeal.wellbeing.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Autentica una petición de MockMvc como una persona concreta, sin pasar por el login. */
public final class Jwts {

    private Jwts() {
    }

    public static RequestPostProcessor as(String userId, String role, String fullName, String organization) {
        return jwt().jwt(token -> token.subject(userId).issuer("zeal").claim("role", role)
                        .claim("fullName", fullName).claim("email", userId + "@zeal.test")
                        .claim("organization", organization))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    public static RequestPostProcessor laura() {
        return as("usr-psicologo-001", "PSICOLOGO", "Laura Martínez", "ZEAL");
    }

    public static RequestPostProcessor employee(String id) {
        return as(id, "EMPLEADO", "Empleado " + id, "Nexura S.A.S.");
    }
}

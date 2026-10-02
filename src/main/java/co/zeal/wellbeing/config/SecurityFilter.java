package co.zeal.wellbeing.config;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SecurityFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public SecurityFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth")) {
            chain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Token requerido\"}");
            return;
        }
        try {
            Claims claims = jwtService.parse(header.substring(7));
            String role = claims.get("role", String.class);
            if (path.startsWith("/api/wellbeing") && "ADMIN".equals(role)) {
                response.setStatus(403);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Acceso denegado al dominio de bienestar\",\"status\":403}");
                return;
            }
            request.setAttribute("userId", claims.getSubject());
            request.setAttribute("userRole", role);
            request.setAttribute("userFullName", claims.get("fullName", String.class));
            request.setAttribute("userEmail", claims.get("email", String.class));
            chain.doFilter(request, response);
        } catch (Exception e) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Token inválido o expirado\"}");
        }
    }
}

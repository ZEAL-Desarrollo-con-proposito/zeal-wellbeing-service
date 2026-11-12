package co.zeal.wellbeing.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * API sin estado protegida con JWT. Aislamiento por diseño (HU-11, RNF-08): los roles corporativos
 * (ADMIN) no acceden a nada del Wellbeing Domain. Desde HU-BE-21 (SEG-3) cualquier acceso denegado
 * responde 404, igual que un recurso inexistente, para no revelar qué existe.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] WELLBEING_DOMAIN = {"/api/wellbeing/**", "/api/chats/**"};

    /**
     * {@code /api/auth/**}: login con Microsoft, renovación y logout. Se autentican con la cookie de
     * renovación (o no requieren identidad), así que esta cadena no valida access tokens: un token vencido
     * en la cabecera no debe impedir renovar ni cerrar sesión.
     */
    @Bean
    @Order(1)
    SecurityFilterChain authFilterChain(HttpSecurity http, JsonSecurityErrorHandler errorHandler) throws Exception {
        return http
                .securityMatcher("/api/auth/**")
                .csrf(AbstractHttpConfigurer::disable) // Cookie SameSite=Strict + CORS con orígenes explícitos
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/microsoft", "/api/auth/refresh", "/api/auth/logout")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/microsoft/callback").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().denyAll()) // p. ej. el POST /api/auth/login retirado: 404
                // En esta cadena nadie está autenticado: una ruta inexistente o retirada responde 404
                // (no 401, que haría creer al cliente que basta con iniciar sesión).
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, denied) -> errorHandler.handle(request,
                                response, new AccessDeniedException("ruta de autenticación inexistente")))
                        .accessDeniedHandler(errorHandler))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonSecurityErrorHandler errorHandler)
            throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/error").permitAll()
                        .requestMatchers(WELLBEING_DOMAIN).hasAnyRole(Role.EMPLEADO.name(), Role.PSICOLOGO.name())
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleClaimConverter()))
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler))
                .build();
    }

    /** El claim {@code role} se convierte en la autoridad {@code ROLE_<rol>}. */
    private static JwtAuthenticationConverter roleClaimConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ZealClaims.ROLE);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}

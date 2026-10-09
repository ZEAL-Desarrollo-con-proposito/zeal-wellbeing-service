package co.zeal.wellbeing.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** HU-01: inicio de sesión con los usuarios semilla del MVP. */
@Service
public class AuthService {

    /** Hash válido de una contraseña cualquiera: iguala el tiempo de respuesta si el correo no existe. */
    private static final String TIMING_GUARD_HASH = "$2a$10$ZtcXR060QKqp4hwAJigpmOEcSoZ2K1exOKMh3o4Ncwpt3/oozQ2Fa";

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public AuthService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        UserAccount account = accounts.findByEmail(email).orElse(null);
        String hash = account == null ? TIMING_GUARD_HASH : account.getPasswordHash();
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (account == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        TokenService.IssuedToken token = tokenService.issue(account);
        return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(),
                LoginResponse.UserView.of(account));
    }
}

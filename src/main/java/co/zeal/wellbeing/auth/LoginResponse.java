package co.zeal.wellbeing.auth;

import co.zeal.wellbeing.shared.security.Role;

/** Contrato de {@code POST /api/auth/login} que valida {@code isLoginResponse()} del frontend. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserView user) {

    public record UserView(String id, String fullName, String email, Role role, String organization) {

        static UserView of(UserAccount account) {
            return new UserView(account.getId(), account.getFullName(), account.getEmail(), account.getRole(),
                    account.getOrganization());
        }
    }
}

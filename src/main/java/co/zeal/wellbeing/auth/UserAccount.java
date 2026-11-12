package co.zeal.wellbeing.auth;

import co.zeal.wellbeing.shared.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cuenta precargada por la empresa. La persona se autentica con Microsoft (R2-17): ZEAL no guarda
 * contraseñas, solo reconoce el correo de la cuenta.
 */
@Entity
@Table(name = "user_accounts")
public class UserAccount {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false)
    private String organization;

    protected UserAccount() {
        // JPA
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public String getOrganization() {
        return organization;
    }
}

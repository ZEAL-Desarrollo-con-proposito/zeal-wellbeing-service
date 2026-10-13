package co.zeal.wellbeing.auth;

import co.zeal.wellbeing.shared.security.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {

    Optional<UserAccount> findByEmail(String email);

    List<UserAccount> findByOrganizationAndRoleOrderByFullName(String organization, Role role);
}

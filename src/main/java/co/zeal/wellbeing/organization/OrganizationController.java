package co.zeal.wellbeing.organization;

import co.zeal.wellbeing.auth.UserAccountRepository;
import co.zeal.wellbeing.shared.security.AuthenticatedUser;
import co.zeal.wellbeing.shared.security.CurrentUser;
import co.zeal.wellbeing.shared.security.Role;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Directorio de la empresa para su administrador. Vive en este servicio porque aquí están las
 * cuentas, pero fuera de {@code /api/wellbeing}: solo expone datos de contacto.
 */
@RestController
@RequestMapping("/api/organizacion")
@PreAuthorize("hasRole('ADMIN')")
public class OrganizationController {

    private final UserAccountRepository accounts;

    public OrganizationController(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    /** Empleados de la empresa de quien pregunta, en orden alfabético. */
    @GetMapping("/empleados")
    @Transactional(readOnly = true)
    public List<EmployeeView> employees(@CurrentUser AuthenticatedUser admin) {
        return accounts.findByOrganizationAndRoleOrderByFullName(admin.organization(), Role.EMPLEADO).stream()
                .map(EmployeeView::of)
                .toList();
    }
}

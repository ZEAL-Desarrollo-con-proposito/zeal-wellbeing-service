package co.zeal.wellbeing.support;

import co.zeal.wellbeing.shared.security.AuthenticatedUser;
import co.zeal.wellbeing.shared.security.Role;

public final class Users {

    public static final AuthenticatedUser LAURA =
            new AuthenticatedUser("usr-psicologo-001", Role.PSICOLOGO, "Laura Martínez", "laura.martinez@zeal.co", "ZEAL");

    private Users() {
    }

    public static AuthenticatedUser employee(String id) {
        return new AuthenticatedUser(id, Role.EMPLEADO, "Empleado " + id, id + "@nexura.co", "Nexura S.A.S.");
    }
}

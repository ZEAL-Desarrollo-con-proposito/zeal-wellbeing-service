package co.zeal.wellbeing.organization;

import co.zeal.wellbeing.auth.UserAccount;

/**
 * Lo que la empresa ve de cada empleado: datos de contacto, nunca nada del Wellbeing Domain
 * (ni citas, ni psicólogo, ni mensajes).
 */
public record EmployeeView(String id, String fullName, String email) {

    static EmployeeView of(UserAccount account) {
        return new EmployeeView(account.getId(), account.getFullName(), account.getEmail());
    }
}

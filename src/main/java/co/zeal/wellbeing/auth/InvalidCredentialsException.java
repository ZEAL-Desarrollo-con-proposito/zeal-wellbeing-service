package co.zeal.wellbeing.auth;

/** Correo o contraseña incorrectos. Mismo mensaje en ambos casos para no revelar qué cuentas existen. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Correo o contraseña incorrectos");
    }
}

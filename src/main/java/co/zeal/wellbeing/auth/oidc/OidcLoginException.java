package co.zeal.wellbeing.auth.oidc;

/**
 * El login con Microsoft no se completó. {@code code} es lo que ve el SPA en
 * {@code /login?error=<code>}: no distingue entre cuentas que existen o no más allá de lo necesario.
 * {@code reason} solo va a la auditoría.
 */
public class OidcLoginException extends RuntimeException {

    public static final String NOT_REGISTERED = "cuenta_no_registrada";
    public static final String CANCELLED = "cancelado";
    public static final String FAILED = "fallo";

    private final String code;
    private final String reason;

    public OidcLoginException(String code, String reason) {
        super(reason);
        this.code = code;
        this.reason = reason;
    }

    public String code() {
        return code;
    }

    public String reason() {
        return reason;
    }
}

package co.zeal.wellbeing.auth.oidc;

import java.util.regex.Pattern;

/**
 * Ruta de regreso después del login. Solo se aceptan rutas internas del SPA ({@code /empleado/mis-citas}):
 * nada de URLs absolutas, {@code //otro-sitio} (protocol-relative), barras invertidas ni esquemas, para
 * que el login no sirva de redirección abierta.
 */
public final class ReturnPath {

    public static final String DEFAULT = "/";
    private static final Pattern INTERNAL = Pattern.compile("^/(?![/\\\\])[A-Za-z0-9\\-._~/%?=&]*$");
    private static final int MAX_LENGTH = 200;

    private ReturnPath() {
    }

    public static boolean isInternal(String path) {
        return path != null && path.length() <= MAX_LENGTH && INTERNAL.matcher(path).matches()
                && !path.contains("\\") && !path.toLowerCase().contains("%5c") && !path.contains("/../");
    }

    public static String orDefault(String path) {
        return isInternal(path) ? path : DEFAULT;
    }
}

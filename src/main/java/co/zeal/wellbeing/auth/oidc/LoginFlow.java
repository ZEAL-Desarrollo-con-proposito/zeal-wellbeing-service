package co.zeal.wellbeing.auth.oidc;

/**
 * Lo que el navegador debe traer de vuelta del proveedor para cerrar el login: viaja firmado en una
 * cookie corta httpOnly ({@link LoginFlowCookie}) y nunca se guarda en el servidor.
 */
public record LoginFlow(String state, String nonce, String codeVerifier, boolean remember, String returnTo) {
}

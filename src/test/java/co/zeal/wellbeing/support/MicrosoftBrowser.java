package co.zeal.wellbeing.support;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hace lo que el navegador hace en la página de Microsoft: sigue la URL de autorización, "inicia
 * sesión" en el proveedor de prueba con los claims indicados y devuelve los parámetros con los que el
 * proveedor redirige al callback de ZEAL ({@code code}, {@code state}).
 */
public final class MicrosoftBrowser {

    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private MicrosoftBrowser() {
    }

    public static Map<String, String> signIn(String authorizationUrl, String claimsJson) throws Exception {
        String form = "username=" + URLEncoder.encode("persona", StandardCharsets.UTF_8)
                + "&claims=" + URLEncoder.encode(claimsJson, StandardCharsets.UTF_8);
        HttpResponse<Void> response = HTTP.send(HttpRequest.newBuilder(URI.create(authorizationUrl))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.discarding());
        String location = response.headers().firstValue("Location")
                .orElseThrow(() -> new IllegalStateException("El proveedor no redirigió: " + response.statusCode()));
        return query(URI.create(location).getRawQuery());
    }

    public static Map<String, String> query(String rawQuery) {
        return Arrays.stream(rawQuery.split("&")).map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }
}

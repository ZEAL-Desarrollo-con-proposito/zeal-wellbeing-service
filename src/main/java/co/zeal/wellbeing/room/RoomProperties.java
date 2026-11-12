package co.zeal.wellbeing.room;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Reglas de la sala virtual: abre {@code opensBeforeStart} antes de la sesión y cierra al
 * terminarla. {@code iceServers} son los servidores STUN; {@code turn} es el relé coturn
 * permanente (HU-DT-02) para redes que bloquean la conexión directa.
 */
@Validated
@ConfigurationProperties("app.room")
public record RoomProperties(@NotNull Duration opensBeforeStart, List<String> iceServers, Turn turn) {

    public RoomProperties {
        iceServers = iceServers == null ? List.of() : List.copyOf(iceServers);
        turn = turn == null ? new Turn(null, null, null) : turn;
    }

    /**
     * Servidores ICE que recibe el navegador: los STUN y, si está configurado, el TURN con
     * credenciales temporales emitidas para esa persona.
     */
    public List<RoomView.IceServer> browserIceServers(String userId, Instant now) {
        List<RoomView.IceServer> servers = new ArrayList<>();
        iceServers.stream().filter(url -> !url.isBlank()).map(RoomView.IceServer::new).forEach(servers::add);
        if (turn.isConfigured()) {
            Turn.Credentials credentials = turn.credentialsFor(userId, now);
            turn.urls().stream().filter(url -> !url.isBlank())
                    .map(url -> new RoomView.IceServer(url, credentials.username(), credentials.credential()))
                    .forEach(servers::add);
        }
        return List.copyOf(servers);
    }

    /**
     * Relé TURN con credenciales temporales (REST API de coturn, {@code use-auth-secret}): el usuario
     * es {@code <vencimiento-unix>:<id>} y la clave {@code base64(HMAC-SHA1(secreto, usuario))}. coturn
     * la verifica con el mismo secreto y la rechaza al vencer; en la configuración no hay ninguna
     * contraseña fija.
     */
    public record Turn(List<String> urls, String secret, Duration ttl) {

        public Turn {
            urls = urls == null ? List.of() : List.copyOf(urls);
            ttl = ttl == null ? Duration.ofHours(2) : ttl;
        }

        boolean isConfigured() {
            return urls.stream().anyMatch(url -> !url.isBlank()) && secret != null && !secret.isBlank();
        }

        Credentials credentialsFor(String userId, Instant now) {
            String username = now.plus(ttl).getEpochSecond() + ":" + userId;
            try {
                Mac mac = Mac.getInstance("HmacSHA1");
                mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
                String credential = Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
                return new Credentials(username, credential);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("No se pudo firmar la credencial TURN", e);
            }
        }

        record Credentials(String username, String credential) {
        }
    }

    public Instant opensAt(Instant sessionStart) {
        return sessionStart.minus(opensBeforeStart);
    }
}

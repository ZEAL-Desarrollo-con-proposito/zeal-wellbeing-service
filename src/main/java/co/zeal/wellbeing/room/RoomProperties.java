package co.zeal.wellbeing.room;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reglas de la sala virtual: abre {@code opensBeforeStart} antes de la sesión y cierra al
 * terminarla. {@code iceServers} son los servidores STUN públicos que usa WebRTC; {@code turn} es
 * el relé opcional para redes que bloquean la conexión directa (NAT simétrico, firewalls corporativos).
 */
@Validated
@ConfigurationProperties("app.room")
public record RoomProperties(@NotNull Duration opensBeforeStart, List<String> iceServers, Turn turn) {

    public RoomProperties {
        iceServers = iceServers == null ? List.of() : List.copyOf(iceServers);
        turn = turn == null ? new Turn(null, null, null) : turn;
    }

    /** Servidores ICE que recibe el navegador: los STUN y, si está configurado, el TURN con sus credenciales. */
    public List<RoomView.IceServer> browserIceServers() {
        List<RoomView.IceServer> servers = new ArrayList<>();
        iceServers.stream().filter(url -> !url.isBlank()).map(RoomView.IceServer::new).forEach(servers::add);
        if (turn.isConfigured()) {
            turn.urls().stream().filter(url -> !url.isBlank())
                    .map(url -> new RoomView.IceServer(url, turn.username(), turn.credential()))
                    .forEach(servers::add);
        }
        return List.copyOf(servers);
    }

    public record Turn(List<String> urls, String username, String credential) {

        public Turn {
            urls = urls == null ? List.of() : List.copyOf(urls);
        }

        boolean isConfigured() {
            return urls.stream().anyMatch(url -> !url.isBlank()) && username != null && !username.isBlank()
                    && credential != null && !credential.isBlank();
        }
    }

    public Instant opensAt(Instant sessionStart) {
        return sessionStart.minus(opensBeforeStart);
    }
}

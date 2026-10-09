package co.zeal.wellbeing.room;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Reglas de la sala virtual: abre {@code opensBeforeStart} antes de la sesión y cierra al
 * terminarla. {@code iceServers} son los servidores STUN/TURN que usa WebRTC.
 */
@Validated
@ConfigurationProperties("app.room")
public record RoomProperties(@NotNull Duration opensBeforeStart, List<String> iceServers) {

    public RoomProperties {
        iceServers = iceServers == null ? List.of() : List.copyOf(iceServers);
    }

    public Instant opensAt(Instant sessionStart) {
        return sessionStart.minus(opensBeforeStart);
    }
}

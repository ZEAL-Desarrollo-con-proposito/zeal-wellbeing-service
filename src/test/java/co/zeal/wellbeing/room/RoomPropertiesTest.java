package co.zeal.wellbeing.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoomPropertiesTest {

    private static final List<String> STUN = List.of("stun:stun.l.google.com:19302");

    private static final Instant NOW = Instant.parse("2026-11-11T15:00:00Z");

    @Test
    void withoutTurnOnlyStunServersAreSent() {
        RoomProperties properties = new RoomProperties(Duration.ofMinutes(10), STUN, null);

        assertThat(properties.browserIceServers("emp", NOW))
                .containsExactly(new RoomView.IceServer("stun:stun.l.google.com:19302"));
    }

    @Test
    void turnWithoutSecretIsIgnored() {
        RoomProperties properties = new RoomProperties(Duration.ofMinutes(10), STUN,
                new RoomProperties.Turn(List.of("turn:turn.zeal.co:3478"), " ", null));

        assertThat(properties.browserIceServers("emp", NOW)).hasSize(1);
    }

    /**
     * HU-DT-02: credenciales temporales de la REST API de coturn ({@code use-auth-secret}). Vector
     * calculado aparte: {@code echo -n "1794416400:usr-empleado-001" | openssl dgst -sha1 -hmac
     * "secreto-de-prueba" -binary | base64}.
     */
    @Test
    void turnCredentialsAreTemporaryAndSignedWithTheSharedSecret() {
        RoomProperties properties = new RoomProperties(Duration.ofMinutes(10), STUN,
                new RoomProperties.Turn(List.of("turn:turn.zeal.co:3478", "turns:turn.zeal.co:443", ""),
                        "secreto-de-prueba", Duration.ofHours(2)));

        List<RoomView.IceServer> servers = properties.browserIceServers("usr-empleado-001", NOW);

        String username = NOW.plus(Duration.ofHours(2)).getEpochSecond() + ":usr-empleado-001";
        assertThat(username).isEqualTo("1794416400:usr-empleado-001");
        assertThat(servers).containsExactly(
                new RoomView.IceServer("stun:stun.l.google.com:19302"),
                new RoomView.IceServer("turn:turn.zeal.co:3478", username, "7xeTHjtYqTI7mteIX7sdkuqKl6Q="),
                new RoomView.IceServer("turns:turn.zeal.co:443", username, "7xeTHjtYqTI7mteIX7sdkuqKl6Q="));
    }

    /** El guard isIceServer() del frontend acepta username/credential ausentes, pero no null. */
    @Test
    void stunServerIsSerializedWithoutNullCredentials() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new RoomView.IceServer("stun:stun.l.google.com:19302"));

        assertThat(json).isEqualTo("{\"urls\":\"stun:stun.l.google.com:19302\"}");
    }
}

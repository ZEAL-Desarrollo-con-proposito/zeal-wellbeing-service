package co.zeal.wellbeing.room;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoomPropertiesTest {

    private static final List<String> STUN = List.of("stun:stun.l.google.com:19302");

    @Test
    void withoutTurnOnlyStunServersAreSent() {
        RoomProperties properties = new RoomProperties(Duration.ofMinutes(10), STUN, null);

        assertThat(properties.browserIceServers())
                .containsExactly(new RoomView.IceServer("stun:stun.l.google.com:19302"));
    }

    @Test
    void incompleteTurnIsIgnored() {
        RoomProperties properties = new RoomProperties(Duration.ofMinutes(10), STUN,
                new RoomProperties.Turn(List.of("turn:turn.zeal.co:3478"), "zeal", " "));

        assertThat(properties.browserIceServers()).hasSize(1);
    }

    @Test
    void configuredTurnIsAddedWithItsCredentials() {
        RoomProperties properties = new RoomProperties(Duration.ofMinutes(10), STUN,
                new RoomProperties.Turn(List.of("turn:turn.zeal.co:3478", ""), "zeal", "secreto"));

        assertThat(properties.browserIceServers()).containsExactly(
                new RoomView.IceServer("stun:stun.l.google.com:19302"),
                new RoomView.IceServer("turn:turn.zeal.co:3478", "zeal", "secreto"));
    }

    /** El guard isIceServer() del frontend acepta username/credential ausentes, pero no null. */
    @Test
    void stunServerIsSerializedWithoutNullCredentials() throws Exception {
        String json = new ObjectMapper().writeValueAsString(new RoomView.IceServer("stun:stun.l.google.com:19302"));

        assertThat(json).isEqualTo("{\"urls\":\"stun:stun.l.google.com:19302\"}");
    }
}

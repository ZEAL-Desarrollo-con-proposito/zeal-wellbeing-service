package co.zeal.wellbeing.room;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Sala vista por uno de sus participantes: contrato de {@code isRoom()} del frontend.
 * {@code participant} es siempre la otra persona de la sesión.
 */
public record RoomView(String reservationId, RoomStatus status, Instant startsAt, Instant endsAt, Instant opensAt,
                       Instant closesAt, Participant participant, List<IceServer> iceServers) {

    public record Participant(String id, String fullName) {
    }

    /** Sin credenciales (STUN) los campos se omiten: el frontend acepta ausentes, no {@code null}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IceServer(String urls, String username, String credential) {

        public IceServer(String urls) {
            this(urls, null, null);
        }
    }
}

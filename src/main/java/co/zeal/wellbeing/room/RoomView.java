package co.zeal.wellbeing.room;

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

    public record IceServer(String urls) {
    }
}

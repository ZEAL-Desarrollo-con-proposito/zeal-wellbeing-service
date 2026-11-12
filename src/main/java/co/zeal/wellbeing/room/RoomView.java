package co.zeal.wellbeing.room;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Sala vista por uno de sus participantes: contrato de {@code isRoom()} del frontend.
 * {@code participant} es siempre la otra persona de la sesión.
 *
 * <p>HU-DT-02 / DT-7: {@code connection} es la entrada a la sala de LiveKit de la cita, con la misma
 * forma que la sala de los talleres ({@code {serverUrl, token}}). Solo viene cuando la persona puede
 * entrar ya: la sala está ACTIVA (la activó el psicólogo) y dentro de la ventana de ingreso; en
 * cualquier otro caso es {@code null}. {@code iceServers} se mantiene por compatibilidad con el
 * cliente WebRTC punto a punto mientras el frontend migra a LiveKit.
 */
public record RoomView(String reservationId, RoomStatus status, Instant startsAt, Instant endsAt, Instant opensAt,
                       Instant closesAt, Participant participant, List<IceServer> iceServers, Connection connection) {

    public record Participant(String id, String fullName) {
    }

    /** Sin credenciales (STUN) los campos se omiten: el frontend acepta ausentes, no {@code null}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IceServer(String urls, String username, String credential) {

        public IceServer(String urls) {
            this(urls, null, null);
        }
    }

    /** URL del servidor de LiveKit y permiso de entrada firmado para esta persona y esta sala. */
    public record Connection(String serverUrl, String token) {
    }
}

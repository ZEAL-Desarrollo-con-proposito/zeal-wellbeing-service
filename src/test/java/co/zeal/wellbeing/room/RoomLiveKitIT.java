package co.zeal.wellbeing.room;

import co.zeal.wellbeing.reservation.Reservation;
import co.zeal.wellbeing.reservation.ReservationRepository;
import co.zeal.wellbeing.slot.Slot;
import co.zeal.wellbeing.slot.SlotRepository;
import co.zeal.wellbeing.support.IntegrationTest;
import co.zeal.wellbeing.support.Jwts;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DT-7: el permiso de LiveKit de la cita 1:1 solo se emite a sus dos participantes, dentro de la
 * ventana de ingreso y con la sala activada por el psicólogo. En cualquier otro caso: 404.
 */
@AutoConfigureMockMvc
class RoomLiveKitIT extends IntegrationTest {

    private static final String EMPLOYEE = "usr-empleado-001";

    @Autowired
    MockMvc mvc;
    @Autowired
    SlotRepository slots;
    @Autowired
    ReservationRepository reservations;

    @Test
    void connectionIsIssuedOnlyAfterThePsychologistActivatesTheRoom() throws Exception {
        String id = reservation(Instant.now().minus(5, ChronoUnit.MINUTES), false);

        mvc.perform(get("/api/wellbeing/reservas/{id}/sala", id).with(Jwts.employee(EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROGRAMADA"))
                .andExpect(jsonPath("$.connection").isEmpty());
        mvc.perform(get("/api/wellbeing/reservas/{id}/sala/conexion", id).with(Jwts.employee(EMPLOYEE)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/wellbeing/reservas/{id}/sala/activar", id).with(Jwts.laura()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connection.serverUrl").value("/livekit"));

        String body = mvc.perform(get("/api/wellbeing/reservas/{id}/sala", id).with(Jwts.employee(EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVA"))
                .andReturn().getResponse().getContentAsString();
        Map<String, Object> claims = SignedJWT.parse(JsonPath.read(body, "$.connection.token")).getJWTClaimsSet().getClaims();
        @SuppressWarnings("unchecked")
        Map<String, Object> video = (Map<String, Object>) claims.get("video");
        assertThat(claims).containsEntry("sub", EMPLOYEE).containsEntry("name", "Empleado " + EMPLOYEE);
        assertThat(video).containsEntry("room", "cita-" + id).containsEntry("roomJoin", true)
                .containsEntry("roomAdmin", false);

        mvc.perform(get("/api/wellbeing/reservas/{id}/sala/conexion", id).with(Jwts.laura()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverUrl").value("/livekit"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void theRoomNameIsDeterministicPerReservation() throws Exception {
        String id = reservation(Instant.now().minus(1, ChronoUnit.MINUTES), true);

        String first = tokenRoom(id, Jwts.employee(EMPLOYEE));
        String second = tokenRoom(id, Jwts.laura());

        assertThat(first).isEqualTo(second).isEqualTo(RoomService.liveKitRoomName(id)).isEqualTo("cita-" + id);
    }

    @Test
    void anyoneButTheTwoParticipantsGets404() throws Exception {
        String id = reservation(Instant.now().minus(5, ChronoUnit.MINUTES), true);

        mvc.perform(get("/api/wellbeing/reservas/{id}/sala/conexion", id).with(Jwts.employee("usr-empleado-002")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/wellbeing/reservas/{id}/sala", id).with(Jwts.employee("usr-empleado-002")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/wellbeing/reservas/{id}/sala/conexion", id)
                        .with(Jwts.as("usr-psicologo-002", "PSICOLOGO", "Otro psicólogo", "ZEAL")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/wellbeing/reservas/{id}/sala/conexion", "reserva-inexistente").with(Jwts.employee(EMPLOYEE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void outsideTheEntryWindowThereIsNoConnection() throws Exception {
        // Activada pero ya terminada.
        String finished = reservation(Instant.now().minus(2, ChronoUnit.HOURS), true);
        mvc.perform(get("/api/wellbeing/reservas/{id}/sala", finished).with(Jwts.employee(EMPLOYEE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZADA"))
                .andExpect(jsonPath("$.connection").isEmpty());
        mvc.perform(get("/api/wellbeing/reservas/{id}/sala/conexion", finished).with(Jwts.employee(EMPLOYEE)))
                .andExpect(status().isNotFound());

        // Todavía no abre: el psicólogo no puede activarla y no hay permiso.
        String upcoming = reservation(Instant.now().plus(2, ChronoUnit.HOURS), false);
        mvc.perform(post("/api/wellbeing/reservas/{id}/sala/activar", upcoming).with(Jwts.laura()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/wellbeing/reservas/{id}/sala/conexion", upcoming).with(Jwts.laura()))
                .andExpect(status().isNotFound());
    }

    private String tokenRoom(String id, org.springframework.test.web.servlet.request.RequestPostProcessor who)
            throws Exception {
        String body = mvc.perform(get("/api/wellbeing/reservas/{id}/sala/conexion", id).with(who))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        Map<String, Object> video = (Map<String, Object>) SignedJWT.parse(JsonPath.read(body, "$.token"))
                .getJWTClaimsSet().getClaim("video");
        return (String) video.get("room");
    }

    /** Una cita de 50 min de Laura con David que empieza en {@code start}, opcionalmente ya activada. */
    private String reservation(Instant start, boolean active) {
        Slot slot = slots.save(Slot.open("usr-psicologo-001", start.truncatedTo(ChronoUnit.SECONDS),
                start.truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofMinutes(50))));
        Reservation reservation = Reservation.confirm(slot, EMPLOYEE, "Empleado " + EMPLOYEE, Instant.now());
        if (active) {
            reservation.activateRoom(start);
        }
        return reservations.save(reservation).getId();
    }
}

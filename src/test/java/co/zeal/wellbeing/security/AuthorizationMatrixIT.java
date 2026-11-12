package co.zeal.wellbeing.security;

import co.zeal.wellbeing.reservation.Reservation;
import co.zeal.wellbeing.reservation.ReservationRepository;
import co.zeal.wellbeing.slot.Slot;
import co.zeal.wellbeing.slot.SlotRepository;
import co.zeal.wellbeing.support.IntegrationTest;
import co.zeal.wellbeing.support.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * R2-10 · Matriz de autorización rol × recurso del Wellbeing Domain (SEG-3). Cada endpoint protegido se
 * recorre con empleado dueño, empleado de la misma empresa, empleado de otra empresa, psicólogo de la
 * cita, otro psicólogo y administradores de ambas empresas. Ante lo ajeno: 404 (nunca 403 ni datos).
 * Corre en el CI con {@code ./mvnw verify}.
 */
@AutoConfigureMockMvc
class AuthorizationMatrixIT extends IntegrationTest {

    enum Actor {
        DAVID("usr-empleado-001", "EMPLEADO", "Nexura S.A.S."),          // dueño de la cita
        MARIANA("usr-empleado-002", "EMPLEADO", "Nexura S.A.S."),        // misma empresa, no es su cita
        ANDREA("usr-empleado-101", "EMPLEADO", "Órbita Labs S.A.S."),    // otra empresa
        LAURA("usr-psicologo-001", "PSICOLOGO", "ZEAL"),                 // psicóloga de la cita
        ANDRES("usr-psicologo-002", "PSICOLOGO", "ZEAL"),                // otro psicólogo
        ADMIN_NEXURA("usr-admin-001", "ADMIN", "Nexura S.A.S."),
        ADMIN_ORBITA("usr-admin-002", "ADMIN", "Órbita Labs S.A.S.");

        final String id;
        final String role;
        final String organization;

        Actor(String id, String role, String organization) {
            this.id = id;
            this.role = role;
            this.organization = organization;
        }

        RequestPostProcessor token() {
            return Jwts.as(id, role, "Persona " + id, organization);
        }
    }

    private static final String RESERVATION = "{reserva}";
    private static final String SLOT = "{franja}";
    private static final String CHAT = "/api/chats/usr-empleado-001:usr-psicologo-001";
    private static final String RANGE = "?desde=" + LocalDate.now() + "&hasta=" + LocalDate.now().plusDays(30);

    @Autowired
    MockMvc mvc;
    @Autowired
    SlotRepository slots;
    @Autowired
    ReservationRepository reservations;

    private Map<String, String> ids;

    @BeforeEach
    void davidHasAnAppointmentWithLaura() {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES)
                .plusSeconds((long) (Math.random() * 100_000) * 60);
        Slot slot = slots.save(Slot.open("usr-psicologo-001", start, start.plus(Duration.ofMinutes(50))));
        Reservation reservation = reservations.save(Reservation.confirm(slot, "usr-empleado-001", "David", Instant.now()));
        Slot free = slots.save(Slot.open("usr-psicologo-001", start.plus(Duration.ofHours(2)),
                start.plus(Duration.ofHours(2)).plus(Duration.ofMinutes(50))));
        ids = Map.of(RESERVATION, reservation.getId(), SLOT, free.getId());
    }

    static Stream<Arguments> matrix() {
        return Stream.of(
                // Sala de la cita: solo sus dos participantes.
                row("GET", "/api/wellbeing/reservas/" + RESERVATION + "/sala", Actor.DAVID, 200),
                row("GET", "/api/wellbeing/reservas/" + RESERVATION + "/sala", Actor.LAURA, 200),
                row("GET", "/api/wellbeing/reservas/" + RESERVATION + "/sala", Actor.MARIANA, 404),
                row("GET", "/api/wellbeing/reservas/" + RESERVATION + "/sala", Actor.ANDREA, 404),
                row("GET", "/api/wellbeing/reservas/" + RESERVATION + "/sala", Actor.ANDRES, 404),
                row("GET", "/api/wellbeing/reservas/" + RESERVATION + "/sala", Actor.ADMIN_NEXURA, 404),
                row("GET", "/api/wellbeing/reservas/" + RESERVATION + "/sala/conexion", Actor.MARIANA, 404),
                row("GET", "/api/wellbeing/reservas/" + RESERVATION + "/sala/conexion", Actor.ADMIN_NEXURA, 404),
                // Activar la sala: solo su psicóloga (409 = autorizada pero aún no abre).
                row("POST", "/api/wellbeing/reservas/" + RESERVATION + "/sala/activar", Actor.LAURA, 409),
                row("POST", "/api/wellbeing/reservas/" + RESERVATION + "/sala/activar", Actor.ANDRES, 404),
                row("POST", "/api/wellbeing/reservas/" + RESERVATION + "/sala/activar", Actor.DAVID, 404),
                row("POST", "/api/wellbeing/reservas/" + RESERVATION + "/sala/activar", Actor.ADMIN_NEXURA, 404),
                // Cancelar: solo el empleado dueño.
                row("POST", "/api/wellbeing/reservas/" + RESERVATION + "/cancelar", Actor.DAVID, 200),
                row("POST", "/api/wellbeing/reservas/" + RESERVATION + "/cancelar", Actor.MARIANA, 404),
                row("POST", "/api/wellbeing/reservas/" + RESERVATION + "/cancelar", Actor.ANDREA, 404),
                row("POST", "/api/wellbeing/reservas/" + RESERVATION + "/cancelar", Actor.LAURA, 404),
                row("POST", "/api/wellbeing/reservas/" + RESERVATION + "/cancelar", Actor.ADMIN_NEXURA, 404),
                // Chat privado: solo sus dos participantes.
                row("GET", CHAT + "/mensajes", Actor.DAVID, 200),
                row("GET", CHAT + "/mensajes", Actor.LAURA, 200),
                row("GET", CHAT + "/mensajes", Actor.MARIANA, 404),
                row("GET", CHAT + "/mensajes", Actor.ANDREA, 404),
                row("GET", CHAT + "/mensajes", Actor.ANDRES, 404),
                row("GET", CHAT + "/mensajes", Actor.ADMIN_NEXURA, 404),
                // Datos propios por rol.
                row("GET", "/api/wellbeing/reservas/mias", Actor.DAVID, 200),
                row("GET", "/api/wellbeing/reservas/mias", Actor.LAURA, 404),
                row("GET", "/api/wellbeing/reservas/mias", Actor.ADMIN_ORBITA, 404),
                row("GET", "/api/wellbeing/reservas/agenda" + RANGE, Actor.LAURA, 200),
                row("GET", "/api/wellbeing/reservas/agenda" + RANGE, Actor.DAVID, 404),
                row("GET", "/api/wellbeing/franjas/mias" + RANGE, Actor.LAURA, 200),
                row("GET", "/api/wellbeing/franjas/mias" + RANGE, Actor.DAVID, 404),
                row("GET", "/api/wellbeing/franjas/mias" + RANGE, Actor.ADMIN_NEXURA, 404),
                // Franja de Laura: otro psicólogo no la puede borrar.
                row("DELETE", "/api/wellbeing/franjas/" + SLOT, Actor.ANDRES, 404),
                row("DELETE", "/api/wellbeing/franjas/" + SLOT, Actor.DAVID, 404),
                row("DELETE", "/api/wellbeing/franjas/" + SLOT, Actor.LAURA, 204),
                // Directorio y cupos liberados: solo empleados; los administradores no ven el Wellbeing Domain.
                row("GET", "/api/wellbeing/psicologos", Actor.DAVID, 200),
                row("GET", "/api/wellbeing/psicologos", Actor.ANDREA, 200),
                row("GET", "/api/wellbeing/psicologos", Actor.LAURA, 404),
                row("GET", "/api/wellbeing/psicologos", Actor.ADMIN_NEXURA, 404),
                row("GET", "/api/wellbeing/cupos", Actor.DAVID, 200),
                row("GET", "/api/wellbeing/cupos", Actor.ADMIN_NEXURA, 404),
                row("POST", "/api/wellbeing/cupos/oferta-ajena/tomar", Actor.MARIANA, 404),
                // Directorio de la empresa: solo su administrador.
                row("GET", "/api/organizacion/empleados", Actor.ADMIN_NEXURA, 200),
                row("GET", "/api/organizacion/empleados", Actor.DAVID, 404),
                row("GET", "/api/organizacion/empleados", Actor.LAURA, 404));
    }

    @ParameterizedTest(name = "{0} {1} como {2} → {3}")
    @MethodSource("matrix")
    void eachRoleOnlyReachesItsOwnResources(String method, String path, Actor actor, int expected) throws Exception {
        String uri = path.replace(RESERVATION, ids.get(RESERVATION)).replace(SLOT, ids.get(SLOT));

        int status = mvc.perform(request(HttpMethod.valueOf(method), uri).with(actor.token()))
                .andReturn().getResponse().getStatus();

        assertThat(status).as("%s %s como %s", method, uri, actor).isEqualTo(expected);
    }

    private static Arguments row(String method, String path, Actor actor, int expected) {
        return Arguments.of(method, path, actor, expected);
    }
}

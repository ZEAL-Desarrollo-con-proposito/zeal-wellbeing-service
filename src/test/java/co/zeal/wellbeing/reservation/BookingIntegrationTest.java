package co.zeal.wellbeing.reservation;

import co.zeal.wellbeing.config.JwtService;
import co.zeal.wellbeing.psychologist.Psychologist;
import co.zeal.wellbeing.psychologist.PsychologistRepository;
import co.zeal.wellbeing.slot.Slot;
import co.zeal.wellbeing.slot.SlotRepository;
import co.zeal.wellbeing.slot.SlotStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wellbeing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Import(BookingIntegrationTest.NoRedisConfiguration.class)
class BookingIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PsychologistRepository psychologistRepository;
    @Autowired private SlotRepository slotRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private BookingService bookingService;

    private Psychologist psychologist;
    private String employeeId;
    private String authorization;
    private static final List<PublishedMessage> PUBLISHED_EVENTS = new CopyOnWriteArrayList<>();

    private record PublishedMessage(String channel, Object payload) {}

    @TestConfiguration
    static class NoRedisConfiguration {

        @Bean
        @Primary
        RedisTemplate<String, Object> testRedisTemplate(RedisConnectionFactory connectionFactory) {
            RedisTemplate<String, Object> template = new RedisTemplate<>() {
                @Override
                public Long convertAndSend(String channel, Object message) {
                    PUBLISHED_EVENTS.add(new PublishedMessage(channel, message));
                    return 1L;
                }
            };
            template.setConnectionFactory(connectionFactory);
            return template;
        }
    }

    @BeforeEach
    void setUp() {
        PUBLISHED_EVENTS.clear();
        reservationRepository.deleteAll();
        slotRepository.deleteAll();
        psychologist = psychologistRepository.save(Psychologist.builder()
                .id("test-psychologist-" + UUID.randomUUID())
                .fullName("Psicóloga de prueba")
                .title("Psicóloga clínica")
                .focusAreas(List.of("Ansiedad"))
                .build());
        employeeId = "test-employee-" + UUID.randomUUID();
        authorization = "Bearer " + jwtService.generate(
                employeeId, "EMPLEADO", "employee@example.test", "Empleado de prueba", "Zeal");
    }

    @Test
    void directoryEndpointAcceptsInclusiveDateRangeAndReturnsReservedSlots() throws Exception {
        Instant day = LocalDate.now(ZoneOffset.UTC).plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant();
        saveSlot("reserved", day.plusSeconds(12 * 3600L), SlotStatus.RESERVADA);
        saveSlot("available", day.plusSeconds(13 * 3600L), SlotStatus.DISPONIBLE);
        LocalDate date = day.atZone(ZoneOffset.UTC).toLocalDate();

        var slotsResult = mockMvc.perform(get("/api/wellbeing/psicologos/{id}/franjas", psychologist.getId())
                        .header("Authorization", authorization)
                        .param("desde", date.toString())
                        .param("hasta", date.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        JsonNode slots = objectMapper.readTree(slotsResult.getResponse().getContentAsString());
        assertEquals("RESERVADA", slots.get(0).get("status").asText());
        assertEquals("DISPONIBLE", slots.get(1).get("status").asText());

        var directoryResult = mockMvc.perform(get("/api/wellbeing/psicologos")
                        .header("Authorization", authorization))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode directory = objectMapper.readTree(directoryResult.getResponse().getContentAsString());
        JsonNode entry = null;
        for (JsonNode psychologistEntry : directory) {
            if (psychologist.getId().equals(psychologistEntry.get("id").asText())) {
                entry = psychologistEntry;
                break;
            }
        }
        assertTrue(entry != null);
        assertEquals(slots.get(1).get("startsAt").asText(),
                entry.get("nextFreeSlot").get("startsAt").asText());
    }

    @Test
    void reservationEndpointReturns409ForAnAlreadyReservedSlot() throws Exception {
        Slot slot = saveSlot("conflict", Instant.now().plusSeconds(2 * 86400L), SlotStatus.DISPONIBLE);
        String request = objectMapper.writeValueAsString(new ReservationController.BookRequest(slot.getId()));

        mockMvc.perform(post("/api/wellbeing/reservas")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());
        JsonNode slotEvent = PUBLISHED_EVENTS.stream()
                .filter(event -> "events.slots".equals(event.channel()))
                .map(event -> asJsonNode(event.payload()))
                .findFirst()
                .orElseThrow();
        assertEquals("ACTUALIZADA", slotEvent.get("type").asText());
        assertEquals("RESERVADA", slotEvent.get("slot").get("status").asText());
        mockMvc.perform(post("/api/wellbeing/reservas")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict());

        Reservation firstReservation = reservationRepository.findAll().get(0);
        bookingService.cancelAppointment(firstReservation.getId(), employeeId);
        mockMvc.perform(post("/api/wellbeing/reservas")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());
        assertEquals(2, reservationRepository.count());
    }

    @Test
    void simultaneousReservationsOnlyCreateOneReservation() throws Exception {
        Slot slot = saveSlot("concurrent", Instant.now().plusSeconds(2 * 86400L), SlotStatus.DISPONIBLE);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = List.of(
                    executor.submit(() -> tryBook(slot.getId(), ready, start)),
                    executor.submit(() -> tryBook(slot.getId(), ready, start)));
            ready.await();
            start.countDown();
            List<Integer> responses = results.stream().map(this::statusCode).toList();

            assertEquals(1, responses.stream().filter(code -> code == 201).count());
            assertEquals(1, responses.stream().filter(code -> code == 409).count());
            assertEquals(1, reservationRepository.count());
            assertEquals(SlotStatus.RESERVADA, slotRepository.findById(slot.getId()).orElseThrow().getStatus());
        }
    }

    @Test
    void cancellationAtomicallyReleasesTheSlotAndRejectsNonOwners() throws Exception {
        Slot slot = saveSlot("cancel", Instant.now().plusSeconds(2 * 86400L), SlotStatus.DISPONIBLE);
        String request = objectMapper.writeValueAsString(new ReservationController.BookRequest(slot.getId()));
        mockMvc.perform(post("/api/wellbeing/reservas")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());
        Reservation reservation = reservationRepository.findAll().get(0);
        String cancelPath = "/api/wellbeing/reservas/" + reservation.getId() + "/cancelar";
        String otherEmployee = "Bearer " + jwtService.generate(
                "different-employee", "EMPLEADO", "other@example.test", "Otro empleado", "Zeal");

        mockMvc.perform(post(cancelPath).header("Authorization", otherEmployee))
                .andExpect(status().isForbidden());
        assertEquals(ReservationStatus.CONFIRMADA,
                reservationRepository.findById(reservation.getId()).orElseThrow().getStatus());
        assertEquals(SlotStatus.RESERVADA, slotRepository.findById(slot.getId()).orElseThrow().getStatus());

        mockMvc.perform(post(cancelPath).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
        assertEquals(ReservationStatus.CANCELADA,
                reservationRepository.findById(reservation.getId()).orElseThrow().getStatus());
        assertEquals(SlotStatus.DISPONIBLE, slotRepository.findById(slot.getId()).orElseThrow().getStatus());

        JsonNode slotEvent = PUBLISHED_EVENTS.stream()
                .filter(event -> "events.slots".equals(event.channel()))
                .map(event -> asJsonNode(event.payload()))
                .filter(event -> slot.getId().equals(event.path("slot").path("id").asText()))
                .reduce((first, second) -> second)
                .orElseThrow();
        assertEquals("DISPONIBLE", slotEvent.get("slot").get("status").asText());
    }

    private Slot saveSlot(String suffix, Instant startsAt, SlotStatus status) {
        return slotRepository.save(Slot.builder()
                .id("test-slot-" + suffix + "-" + UUID.randomUUID())
                .psychologistId(psychologist.getId())
                .startsAt(startsAt)
                .endsAt(startsAt.plusSeconds(3600))
                .status(status)
                .version(0L)
                .build());
    }

    private int tryBook(String slotId, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        String request = objectMapper.writeValueAsString(new ReservationController.BookRequest(slotId));
        return mockMvc.perform(post("/api/wellbeing/reservas")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int statusCode(Future<Integer> result) {
        try {
            return result.get();
        } catch (Exception exception) {
            throw new AssertionError("Concurrent booking request failed", exception);
        }
    }

    private JsonNode asJsonNode(Object value) {
        return objectMapper.valueToTree(value);
    }
}

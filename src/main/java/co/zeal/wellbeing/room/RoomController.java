package co.zeal.wellbeing.room;

import co.zeal.wellbeing.psychologist.Psychologist;
import co.zeal.wellbeing.psychologist.PsychologistRepository;
import co.zeal.wellbeing.reservation.Reservation;
import co.zeal.wellbeing.reservation.ReservationRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wellbeing/reservas")
public class RoomController {

    private final ReservationRepository reservationRepository;
    private final PsychologistRepository psychologistRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public RoomController(ReservationRepository reservationRepository,
                          PsychologistRepository psychologistRepository,
                          RedisTemplate<String, Object> redisTemplate) {
        this.reservationRepository = reservationRepository;
        this.psychologistRepository = psychologistRepository;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/{id}/sala")
    public RoomDto getRoom(@PathVariable String id, HttpServletRequest req) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Psychologist psi = psychologistRepository.findById(reservation.getPsychologistId()).orElseThrow();
        String roomStatus = switch (reservation.getStatus()) {
            case CONFIRMADA -> "PROGRAMADA";
            case CANCELADA -> "CANCELADA";
            case FINALIZADA -> "FINALIZADA";
        };
        return new RoomDto(
                reservation.getId(), roomStatus,
                reservation.getStartsAt().toString(), reservation.getEndsAt().toString(),
                reservation.getStartsAt().minus(15, ChronoUnit.MINUTES).toString(),
                reservation.getEndsAt().plus(5, ChronoUnit.MINUTES).toString(),
                new ParticipantDto(psi.getId(), psi.getFullName()),
                List.of(new IceServerDto("stun:stun.l.google.com:19302"))
        );
    }

    @PostMapping("/{id}/sala/activar")
    public RoomDto activateRoom(@PathVariable String id, HttpServletRequest req) {
        String role = (String) req.getAttribute("userRole");
        if (!"PSICOLOGO".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el psicólogo puede activar la sala.");
        }
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Psychologist psi = psychologistRepository.findById(reservation.getPsychologistId()).orElseThrow();
        redisTemplate.convertAndSend("events.room_activated", Map.of(
                "type", "SALA_ACTUALIZADA",
                "reservationId", reservation.getId(),
                "participantIds", List.of(reservation.getEmployeeId(), psi.getId())
        ));
        return new RoomDto(
                reservation.getId(), "ACTIVA",
                reservation.getStartsAt().toString(), reservation.getEndsAt().toString(),
                reservation.getStartsAt().minus(15, ChronoUnit.MINUTES).toString(),
                reservation.getEndsAt().plus(5, ChronoUnit.MINUTES).toString(),
                new ParticipantDto(psi.getId(), psi.getFullName()),
                List.of(new IceServerDto("stun:stun.l.google.com:19302"))
        );
    }

    public record RoomDto(String reservationId, String status, String startsAt, String endsAt,
                           String opensAt, String closesAt, ParticipantDto participant,
                           List<IceServerDto> iceServers) {}
    public record ParticipantDto(String id, String fullName) {}
    public record IceServerDto(String urls) {}
}

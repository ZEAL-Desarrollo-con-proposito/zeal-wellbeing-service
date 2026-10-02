package co.zeal.wellbeing.reservation;

import co.zeal.wellbeing.freedslot.FreedSlotOffer;
import co.zeal.wellbeing.freedslot.FreedSlotOfferRepository;
import co.zeal.wellbeing.freedslot.FreedSlotStatus;
import co.zeal.wellbeing.psychologist.Psychologist;
import co.zeal.wellbeing.psychologist.PsychologistRepository;
import co.zeal.wellbeing.slot.Slot;
import co.zeal.wellbeing.slot.SlotRepository;
import co.zeal.wellbeing.slot.SlotStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final SlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final FreedSlotOfferRepository freedSlotOfferRepository;
    private final PsychologistRepository psychologistRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public BookingService(SlotRepository slotRepository,
                          ReservationRepository reservationRepository,
                          FreedSlotOfferRepository freedSlotOfferRepository,
                          PsychologistRepository psychologistRepository,
                          RedisTemplate<String, Object> redisTemplate) {
        this.slotRepository = slotRepository;
        this.reservationRepository = reservationRepository;
        this.freedSlotOfferRepository = freedSlotOfferRepository;
        this.psychologistRepository = psychologistRepository;
        this.redisTemplate = redisTemplate;
    }

    private Map<String, Object> slotPayload(Slot slot) {
        return Map.of("id", slot.getId(), "psychologistId", slot.getPsychologistId(),
                "startsAt", slot.getStartsAt().toString(),
                "endsAt", slot.getEndsAt().toString(),
                "status", slot.getStatus().name());
    }

    /** HU-05 / RETO C-2: Reserva atómica con bloqueo pesimista */
    @Transactional
    public Reservation bookSlot(String slotId, String employeeId, String employeeName) {
        Slot slot = slotRepository.findByIdWithLock(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Este bloque ya no existe. Elige otro."));
        if (slot.getStatus() != SlotStatus.DISPONIBLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Otra persona reservó este bloque antes que tú. Elige otro bloque libre.");
        }
        if (slot.getStartsAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Este bloque ya empezó. Elige otro.");
        }
        slot.setStatus(SlotStatus.RESERVADA);
        slotRepository.save(slot);

        Reservation reservation = Reservation.builder()
                .id("reserva-" + UUID.randomUUID().toString().substring(0, 8))
                .slotId(slot.getId())
                .psychologistId(slot.getPsychologistId())
                .employeeId(employeeId)
                .employeeName(employeeName)
                .startsAt(slot.getStartsAt())
                .endsAt(slot.getEndsAt())
                .status(ReservationStatus.CONFIRMADA)
                .build();
        reservationRepository.save(reservation);

        redisTemplate.convertAndSend("events.slots", Map.of("type", "ACTUALIZADA", "slot", slotPayload(slot)));
        redisTemplate.convertAndSend("events.notifications", Map.of(
                "userId", employeeId,
                "notification", Map.of(
                        "id", UUID.randomUUID().toString(),
                        "type", "RESERVA_CONFIRMADA",
                        "title", "Tu cita quedó confirmada",
                        "message", "Con Laura Martínez · " + slot.getStartsAt(),
                        "link", "/empleado/mis-citas",
                        "read", false,
                        "createdAt", Instant.now().toString()
                )
        ));
        return reservation;
    }

    /** HU-07 & HU-08 / RETO C-3: Cancelación + detección de candidatos + alerta broadcast */
    @Transactional
    public Reservation cancelAppointment(String reservationId, String requestingUserId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cita no encontrada."));
        if (!reservation.getEmployeeId().equals(requestingUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes cancelar citas ajenas.");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELADA) {
            return reservation;
        }
        if (Instant.now().plus(24, ChronoUnit.HOURS).isAfter(reservation.getStartsAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya no puedes cancelar esta cita: faltan menos de 24 horas.");
        }
        reservation.setStatus(ReservationStatus.CANCELADA);
        reservationRepository.save(reservation);

        slotRepository.findById(reservation.getSlotId()).ifPresent(slot -> {
            slot.setStatus(SlotStatus.DISPONIBLE);
            slotRepository.save(slot);

            Instant endOfDay = reservation.getStartsAt().truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS);
            List<String> candidateIds = reservationRepository.findCandidateIds(
                    slot.getPsychologistId(), slot.getStartsAt(), endOfDay);

            redisTemplate.convertAndSend("events.slots", Map.of("type", "ACTUALIZADA", "slot", slotPayload(slot)));

            if (!candidateIds.isEmpty()) {
                Psychologist psi = psychologistRepository.findById(slot.getPsychologistId()).orElseThrow();
                FreedSlotOffer offer = FreedSlotOffer.builder()
                        .id("oferta-" + UUID.randomUUID().toString().substring(0, 8))
                        .slotId(slot.getId())
                        .psychologistId(psi.getId())
                        .psychologistFullName(psi.getFullName())
                        .psychologistTitle(psi.getTitle())
                        .startsAt(slot.getStartsAt())
                        .endsAt(slot.getEndsAt())
                        .expiresAt(Instant.now().plus(90, ChronoUnit.SECONDS))
                        .status(FreedSlotStatus.ABIERTA)
                        .candidateIds(candidateIds)
                        .build();
                freedSlotOfferRepository.save(offer);
                redisTemplate.convertAndSend("events.freed_slots", Map.of(
                        "offer", Map.of(
                                "id", offer.getId(),
                                "psychologist", Map.of("id", psi.getId(), "fullName", psi.getFullName(), "title", psi.getTitle()),
                                "startsAt", offer.getStartsAt().toString(),
                                "endsAt", offer.getEndsAt().toString(),
                                "expiresAt", offer.getExpiresAt().toString()
                        ),
                        "candidateIds", candidateIds
                ));
            }
        });
        return reservation;
    }

    /** HU-08 / RETO C-3: Carrera por el cupo — solo el primero lo obtiene */
    @Transactional
    public Reservation claimFreedSlot(String offerId, String employeeId, String employeeName) {
        FreedSlotOffer offer = freedSlotOfferRepository.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oferta no encontrada."));
        if (!offer.getCandidateIds().contains(employeeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No fuiste notificado de este cupo.");
        }
        if (offer.getStatus() != FreedSlotStatus.ABIERTA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Otra persona tomó este cupo antes que tú.");
        }
        if (Instant.now().isAfter(offer.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El tiempo para tomar este cupo venció.");
        }
        Slot slot = slotRepository.findAvailableWithLock(offer.getSlotId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Otra persona tomó este cupo primero."));
        slot.setStatus(SlotStatus.RESERVADA);
        slotRepository.save(slot);
        offer.setStatus(FreedSlotStatus.TOMADA);
        freedSlotOfferRepository.save(offer);
        Reservation reservation = Reservation.builder()
                .id("reserva-" + UUID.randomUUID().toString().substring(0, 8))
                .slotId(slot.getId())
                .psychologistId(slot.getPsychologistId())
                .employeeId(employeeId)
                .employeeName(employeeName)
                .startsAt(slot.getStartsAt())
                .endsAt(slot.getEndsAt())
                .status(ReservationStatus.CONFIRMADA)
                .build();
        reservationRepository.save(reservation);
        redisTemplate.convertAndSend("events.slots", Map.of("type", "ACTUALIZADA", "slot", slotPayload(slot)));
        redisTemplate.convertAndSend("events.freed_slots_taken", Map.of(
                "offerId", offerId, "winnerId", employeeId, "allCandidateIds", offer.getCandidateIds()));
        return reservation;
    }
}

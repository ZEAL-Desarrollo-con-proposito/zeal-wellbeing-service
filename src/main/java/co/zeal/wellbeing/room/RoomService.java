package co.zeal.wellbeing.room;

import co.zeal.wellbeing.psychologist.Psychologist;
import co.zeal.wellbeing.psychologist.PsychologistRepository;
import co.zeal.wellbeing.reservation.Reservation;
import co.zeal.wellbeing.reservation.ReservationRepository;
import co.zeal.wellbeing.reservation.ReservationStatus;
import co.zeal.wellbeing.shared.error.BusinessConflictException;
import co.zeal.wellbeing.shared.error.ResourceNotFoundException;
import co.zeal.wellbeing.shared.security.AuthenticatedUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * HU-12: sala de la videollamada 1:1. Solo los dos participantes de la cita pueden verla (a
 * cualquier otra persona se le responde 404, sin revelar que existe) y solo su psicólogo la activa.
 */
@Service
public class RoomService {

    static final String NOT_FOUND = "Esta sesión no existe o no es tuya.";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm");

    private final ReservationRepository reservations;
    private final PsychologistRepository psychologists;
    private final RoomProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId zone;

    public RoomService(ReservationRepository reservations, PsychologistRepository psychologists,
                       RoomProperties properties, ApplicationEventPublisher events, Clock clock,
                       ZoneId businessZone) {
        this.reservations = reservations;
        this.psychologists = psychologists;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        this.zone = businessZone;
    }

    @Transactional(readOnly = true)
    public RoomView room(AuthenticatedUser user, String reservationId) {
        Reservation reservation = reservations.findById(reservationId)
                .filter(candidate -> candidate.involves(user.id()))
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        return toView(reservation, user, Instant.now(clock));
    }

    /** Solo el psicólogo de la sesión la activa, y solo dentro de la ventana de ingreso. */
    @Transactional
    public RoomView activate(AuthenticatedUser psychologist, String reservationId) {
        Instant now = Instant.now(clock);
        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .filter(candidate -> candidate.getPsychologistId().equals(psychologist.id()))
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        RoomStatus status = statusOf(reservation, now);
        if (status == RoomStatus.CANCELADA) {
            throw new BusinessConflictException("Esta sesión fue cancelada.");
        }
        if (status == RoomStatus.FINALIZADA || now.isAfter(reservation.getEndsAt())) {
            throw new BusinessConflictException("La sala de esta sesión ya cerró.");
        }
        Instant opensAt = properties.opensAt(reservation.getStartsAt());
        if (now.isBefore(opensAt)) {
            throw new BusinessConflictException("La sala abre a las " + TIME.format(opensAt.atZone(zone)) + ".");
        }
        if (reservation.activateRoom(now)) {
            events.publishEvent(new RoomUpdatedEvent(reservation.getId(),
                    List.of(reservation.getPsychologistId(), reservation.getEmployeeId())));
        }
        return toView(reservation, psychologist, now);
    }

    private RoomView toView(Reservation reservation, AuthenticatedUser viewer, Instant now) {
        return new RoomView(
                reservation.getId(),
                statusOf(reservation, now),
                reservation.getStartsAt(),
                reservation.getEndsAt(),
                properties.opensAt(reservation.getStartsAt()),
                reservation.getEndsAt(),
                otherParticipant(reservation, viewer),
                properties.browserIceServers());
    }

    private RoomView.Participant otherParticipant(Reservation reservation, AuthenticatedUser viewer) {
        if (viewer.id().equals(reservation.getPsychologistId())) {
            return new RoomView.Participant(reservation.getEmployeeId(), reservation.getEmployeeName());
        }
        Psychologist psychologist = psychologists.findById(reservation.getPsychologistId())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        return new RoomView.Participant(psychologist.getId(), psychologist.getFullName());
    }

    private static RoomStatus statusOf(Reservation reservation, Instant now) {
        if (reservation.isCancelled()) {
            return RoomStatus.CANCELADA;
        }
        if (reservation.getStatus() == ReservationStatus.FINALIZADA || !reservation.getEndsAt().isAfter(now)) {
            return RoomStatus.FINALIZADA;
        }
        return reservation.isRoomActive() ? RoomStatus.ACTIVA : RoomStatus.PROGRAMADA;
    }
}

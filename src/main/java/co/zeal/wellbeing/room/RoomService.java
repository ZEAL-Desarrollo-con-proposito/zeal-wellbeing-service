package co.zeal.wellbeing.room;

import co.zeal.wellbeing.psychologist.Psychologist;
import co.zeal.wellbeing.shared.video.LiveKitTokenIssuer;
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
    private final LiveKitTokenIssuer liveKit;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId zone;

    public RoomService(ReservationRepository reservations, PsychologistRepository psychologists,
                       RoomProperties properties, LiveKitTokenIssuer liveKit, ApplicationEventPublisher events,
                       Clock clock, ZoneId businessZone) {
        this.reservations = reservations;
        this.psychologists = psychologists;
        this.properties = properties;
        this.liveKit = liveKit;
        this.events = events;
        this.clock = clock;
        this.zone = businessZone;
    }

    @Transactional(readOnly = true)
    public RoomView room(AuthenticatedUser user, String reservationId) {
        return toView(participantReservation(user, reservationId), user, Instant.now(clock));
    }

    /**
     * DT-7: solo la entrada a LiveKit. 404 en cualquier caso en que no se emite permiso (no participa,
     * la sala no está activa o está fuera de la ventana de ingreso), sin distinguir el motivo.
     */
    @Transactional(readOnly = true)
    public RoomView.Connection connection(AuthenticatedUser user, String reservationId) {
        Reservation reservation = participantReservation(user, reservationId);
        Instant now = Instant.now(clock);
        RoomView.Connection connection = connectionFor(reservation, user, statusOf(reservation, now), now);
        if (connection == null) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return connection;
    }

    /** Nombre determinístico de la sala de LiveKit de una reserva. */
    public static String liveKitRoomName(String reservationId) {
        return "cita-" + reservationId;
    }

    private Reservation participantReservation(AuthenticatedUser user, String reservationId) {
        return reservations.findForParticipant(reservationId, user.id())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /** Solo el psicólogo de la sesión la activa, y solo dentro de la ventana de ingreso. */
    @Transactional
    public RoomView activate(AuthenticatedUser psychologist, String reservationId) {
        Instant now = Instant.now(clock);
        Reservation reservation = reservations.findOfPsychologistForUpdate(reservationId, psychologist.id())
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
        RoomStatus status = statusOf(reservation, now);
        return new RoomView(
                reservation.getId(),
                status,
                reservation.getStartsAt(),
                reservation.getEndsAt(),
                properties.opensAt(reservation.getStartsAt()),
                reservation.getEndsAt(),
                otherParticipant(reservation, viewer),
                properties.browserIceServers(viewer.id(), now),
                connectionFor(reservation, viewer, status, now));
    }

    /**
     * Permiso de LiveKit para uno de los dos participantes, solo con la sala activada por el
     * psicólogo y dentro de la ventana de ingreso; vence cuando termina la sesión.
     */
    private RoomView.Connection connectionFor(Reservation reservation, AuthenticatedUser viewer, RoomStatus status,
                                              Instant now) {
        boolean insideWindow = !now.isBefore(properties.opensAt(reservation.getStartsAt()))
                && now.isBefore(reservation.getEndsAt());
        if (status != RoomStatus.ACTIVA || !insideWindow || !reservation.involves(viewer.id())) {
            return null;
        }
        boolean psychologist = viewer.id().equals(reservation.getPsychologistId());
        String metadata = "{\"role\":\"" + (psychologist ? "PSICOLOGO" : "EMPLEADO") + "\"}";
        var grant = new LiveKitTokenIssuer.RoomGrant(liveKitRoomName(reservation.getId()), viewer.id(),
                viewer.fullName(), metadata, psychologist);
        return new RoomView.Connection(liveKit.publicUrl(), liveKit.issue(grant, now, reservation.getEndsAt()));
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

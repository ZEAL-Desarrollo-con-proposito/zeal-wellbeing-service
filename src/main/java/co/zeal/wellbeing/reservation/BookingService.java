package co.zeal.wellbeing.reservation;

import co.zeal.wellbeing.freedslot.FreedSlotService;
import co.zeal.wellbeing.notification.AppointmentNotifications;
import co.zeal.wellbeing.psychologist.PsychologistRepository;
import co.zeal.wellbeing.psychologist.PsychologistSummary;
import co.zeal.wellbeing.shared.error.BusinessConflictException;
import co.zeal.wellbeing.shared.error.BusinessRuleViolationException;
import co.zeal.wellbeing.shared.error.ResourceNotFoundException;
import co.zeal.wellbeing.shared.security.AuthenticatedUser;
import co.zeal.wellbeing.slot.Slot;
import co.zeal.wellbeing.slot.SlotChangedEvent;
import co.zeal.wellbeing.slot.SlotRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Citas del empleado: reservar (HU-05, reto C-2), consultar y cancelar (HU-07, reto C-3). */
@Service
public class BookingService {

    static final String SLOT_GONE = "Este bloque ya no existe. Elige otro.";
    static final String SLOT_TAKEN = "Otra persona reservó este bloque antes que tú. Elige otro bloque libre.";

    private final SlotRepository slots;
    private final ReservationRepository reservations;
    private final PsychologistRepository psychologists;
    private final FreedSlotService freedSlots;
    private final AppointmentNotifications notifications;
    private final BookingProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BookingService(SlotRepository slots, ReservationRepository reservations,
                          PsychologistRepository psychologists, FreedSlotService freedSlots,
                          AppointmentNotifications notifications, BookingProperties properties,
                          ApplicationEventPublisher events, Clock clock) {
        this.slots = slots;
        this.reservations = reservations;
        this.psychologists = psychologists;
        this.freedSlots = freedSlots;
        this.notifications = notifications;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AppointmentView> myAppointments(AuthenticatedUser employee) {
        List<Reservation> own = reservations.findByEmployeeIdOrderByStartsAtAsc(employee.id());
        Map<String, PsychologistSummary> byId = psychologists
                .findAllById(own.stream().map(Reservation::getPsychologistId).distinct().toList())
                .stream()
                .map(PsychologistSummary::of)
                .collect(Collectors.toMap(PsychologistSummary::id, Function.identity()));
        Instant now = Instant.now(clock);
        return own.stream()
                .filter(reservation -> byId.containsKey(reservation.getPsychologistId()))
                .map(reservation -> AppointmentView.of(reservation, byId.get(reservation.getPsychologistId()), now))
                .toList();
    }

    /**
     * Reto C-2 (cero doble reserva): {@code SELECT ... FOR UPDATE} sobre la franja serializa las
     * peticiones concurrentes; solo la primera la encuentra disponible, las demás reciben 409.
     */
    @Transactional
    public AppointmentView book(AuthenticatedUser employee, String slotId) {
        Instant now = Instant.now(clock);
        Slot slot = slots.findByIdForUpdate(slotId).orElseThrow(() -> new ResourceNotFoundException(SLOT_GONE));
        PsychologistSummary psychologist = psychologistOf(slot.getPsychologistId(), SLOT_GONE);
        if (slot.hasStartedAt(now)) {
            throw new BusinessRuleViolationException("Este bloque ya empezó. Elige otro.");
        }
        if (!slot.isAvailable()) {
            throw new BusinessConflictException(SLOT_TAKEN);
        }
        if (reservations.existsConfirmedOverlapping(employee.id(), slot.getStartsAt(), slot.getEndsAt())) {
            throw new BusinessConflictException("Ya tienes una cita que se cruza con ese horario.");
        }
        Reservation reservation = reservations.save(
                Reservation.confirm(slot, employee.id(), employee.fullName(), now));
        events.publishEvent(SlotChangedEvent.updated(slot));
        events.publishEvent(notifications.confirmed(reservation, psychologist));
        events.publishEvent(notifications.bookedForPsychologist(reservation));
        return AppointmentView.of(reservation, psychologist, now);
    }

    /**
     * Cancela una cita propia. Idempotente: si ya estaba cancelada se devuelve tal cual. Al
     * liberar la franja se ofrece a quienes tienen una cita posterior ese día (reto C-3).
     */
    @Transactional
    public AppointmentView cancel(AuthenticatedUser employee, String reservationId) {
        Instant now = Instant.now(clock);
        Reservation reservation = reservations.findOwnedForUpdate(reservationId, employee.id())
                .orElseThrow(() -> new ResourceNotFoundException("Esta cita ya no existe."));
        PsychologistSummary psychologist = psychologistOf(reservation.getPsychologistId(), "Esta cita ya no existe.");
        if (!reservation.isConfirmed()) {
            return AppointmentView.of(reservation, psychologist, now);
        }
        if (!reservation.canBeCancelledAt(now, properties.cancellationNotice())) {
            throw new BusinessConflictException(
                    "Ya no puedes cancelar esta cita: faltan menos de 24 horas para que empiece.");
        }
        reservation.cancel();
        events.publishEvent(notifications.cancelledForPsychologist(reservation));
        slots.findByIdForUpdate(reservation.getSlotId()).ifPresent(slot -> {
            slot.release();
            events.publishEvent(SlotChangedEvent.updated(slot));
            freedSlots.offer(slot, psychologist, employee.id());
        });
        return AppointmentView.of(reservation, psychologist, now);
    }

    private PsychologistSummary psychologistOf(String psychologistId, String notFoundMessage) {
        return psychologists.findById(psychologistId)
                .map(PsychologistSummary::of)
                .orElseThrow(() -> new ResourceNotFoundException(notFoundMessage));
    }
}

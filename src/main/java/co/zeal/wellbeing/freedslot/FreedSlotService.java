package co.zeal.wellbeing.freedslot;

import co.zeal.wellbeing.notification.AppointmentNotifications;
import co.zeal.wellbeing.psychologist.PsychologistRepository;
import co.zeal.wellbeing.psychologist.PsychologistSummary;
import co.zeal.wellbeing.reservation.AppointmentView;
import co.zeal.wellbeing.reservation.BookingProperties;
import co.zeal.wellbeing.reservation.Reservation;
import co.zeal.wellbeing.reservation.ReservationRepository;
import co.zeal.wellbeing.shared.error.BusinessConflictException;
import co.zeal.wellbeing.shared.error.BusinessRuleViolationException;
import co.zeal.wellbeing.shared.error.ResourceGoneException;
import co.zeal.wellbeing.shared.error.ResourceNotFoundException;
import co.zeal.wellbeing.shared.security.AuthenticatedUser;
import co.zeal.wellbeing.slot.Slot;
import co.zeal.wellbeing.slot.SlotChangedEvent;
import co.zeal.wellbeing.slot.SlotRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reto C-3: ofrecer el cupo liberado y resolver la carrera de quienes lo quieren tomar. */
@Service
public class FreedSlotService {

    static final String UNAVAILABLE = "Este cupo ya no está disponible.";
    static final String TAKEN = "Otra persona tomó este cupo antes que tú.";

    private final FreedSlotOfferRepository offers;
    private final SlotRepository slots;
    private final ReservationRepository reservations;
    private final PsychologistRepository psychologists;
    private final AppointmentNotifications notifications;
    private final BookingProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ZoneId zone;

    public FreedSlotService(FreedSlotOfferRepository offers, SlotRepository slots, ReservationRepository reservations,
                            PsychologistRepository psychologists, AppointmentNotifications notifications,
                            BookingProperties properties, ApplicationEventPublisher events, Clock clock,
                            ZoneId businessZone) {
        this.offers = offers;
        this.slots = slots;
        this.reservations = reservations;
        this.psychologists = psychologists;
        this.notifications = notifications;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
        this.zone = businessZone;
    }

    /**
     * Se ejecuta dentro de la transacción de la cancelación: la detección de candidatos y la
     * oferta son atómicas con la liberación de la franja.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void offer(Slot freedSlot, PsychologistSummary psychologist, String releasedBy) {
        Instant endOfDay = freedSlot.getStartsAt().atZone(zone).toLocalDate().plusDays(1)
                .atStartOfDay(zone).toInstant();
        List<String> recipients = reservations.findFreedSlotRecipients(freedSlot.getPsychologistId(), releasedBy,
                freedSlot.getStartsAt(), freedSlot.getEndsAt(), endOfDay);
        if (recipients.isEmpty()) {
            return;
        }
        FreedSlotOffer offer = offers.save(FreedSlotOffer.open(freedSlot, recipients, Instant.now(clock),
                properties.freedSlotOfferWindow()));
        events.publishEvent(new FreedSlotOfferedEvent(FreedSlotOfferView.of(offer, psychologist),
                offer.getRecipientIds()));
    }

    /**
     * La carrera se resuelve con dos bloqueos de fila (oferta y franja): el primero gana (200) y
     * los demás reciben 409. La franja también se bloquea porque alguien podría reservarla desde
     * el directorio al mismo tiempo.
     */
    @Transactional
    public AppointmentView claim(AuthenticatedUser employee, String offerId) {
        Instant now = Instant.now(clock);
        FreedSlotOffer offer = offers.findByIdForUpdate(offerId)
                .filter(candidate -> candidate.isOfferedTo(employee.id()))
                .orElseThrow(() -> new ResourceNotFoundException(UNAVAILABLE));
        if (offer.isTaken()) {
            throw new BusinessConflictException(TAKEN);
        }
        if (offer.isExpiredAt(now)) {
            throw new ResourceGoneException("El tiempo para tomar este cupo ya venció.");
        }
        Slot slot = slots.findByIdForUpdate(offer.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException(UNAVAILABLE));
        PsychologistSummary psychologist = psychologists.findById(offer.getPsychologistId())
                .map(PsychologistSummary::of)
                .orElseThrow(() -> new ResourceNotFoundException(UNAVAILABLE));
        if (!slot.isAvailable()) {
            throw new BusinessConflictException(TAKEN);
        }
        if (reservations.existsConfirmedOverlapping(employee.id(), slot.getStartsAt(), slot.getEndsAt())) {
            throw new BusinessRuleViolationException("Ya tienes una cita que se cruza con ese horario.");
        }
        Reservation reservation = reservations.save(
                Reservation.confirm(slot, employee.id(), employee.fullName(), now));
        offer.markTakenBy(employee.id());
        events.publishEvent(SlotChangedEvent.updated(slot));
        events.publishEvent(new FreedSlotTakenEvent(offer.getId(), employee.id(), offer.getRecipientIds()));
        events.publishEvent(notifications.confirmed(reservation, psychologist));
        return AppointmentView.of(reservation, psychologist, now);
    }

    /** Ofertas abiertas de la persona, para recuperarlas tras reconectar. */
    @Transactional(readOnly = true)
    public List<FreedSlotOfferView> openOffers(AuthenticatedUser employee) {
        List<FreedSlotOffer> open = offers.findOpenOffersFor(employee.id(), Instant.now(clock));
        Map<String, PsychologistSummary> byId = psychologists
                .findAllById(open.stream().map(FreedSlotOffer::getPsychologistId).distinct().toList())
                .stream()
                .map(PsychologistSummary::of)
                .collect(Collectors.toMap(PsychologistSummary::id, Function.identity()));
        return open.stream()
                .filter(offer -> byId.containsKey(offer.getPsychologistId()))
                .map(offer -> FreedSlotOfferView.of(offer, byId.get(offer.getPsychologistId())))
                .toList();
    }
}

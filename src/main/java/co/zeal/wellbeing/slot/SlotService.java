package co.zeal.wellbeing.slot;

import co.zeal.wellbeing.psychologist.PsychologistRepository;
import co.zeal.wellbeing.shared.error.BusinessConflictException;
import co.zeal.wellbeing.shared.error.BusinessRuleViolationException;
import co.zeal.wellbeing.shared.error.ResourceNotFoundException;
import co.zeal.wellbeing.shared.security.AuthenticatedUser;
import co.zeal.wellbeing.shared.time.DateRange;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** HU-02 y HU-03: el psicólogo administra su propia agenda. Cada cambio se difunde en tiempo real. */
@Service
public class SlotService {

    private final SlotRepository slots;
    private final PsychologistRepository psychologists;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public SlotService(SlotRepository slots, PsychologistRepository psychologists, ApplicationEventPublisher events,
                       Clock clock) {
        this.slots = slots;
        this.psychologists = psychologists;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SlotView> mySlots(AuthenticatedUser psychologist, DateRange range) {
        return slots.findInRange(psychologist.id(), range.from(), range.toExclusive()).stream()
                .map(SlotView::of)
                .toList();
    }

    @Transactional
    public SlotView open(AuthenticatedUser psychologist, NewSlotRequest request) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new BusinessRuleViolationException("La hora de fin debe ser posterior a la de inicio");
        }
        if (request.startsAt().isBefore(Instant.now(clock))) {
            throw new BusinessRuleViolationException("No puedes abrir franjas en un horario que ya pasó");
        }
        // Serializa la apertura de franjas del mismo psicólogo antes de comprobar solapes.
        psychologists.findByIdForUpdate(psychologist.id())
                .orElseThrow(() -> new ResourceNotFoundException("Tu perfil de psicólogo no está registrado."));
        if (slots.existsOverlapping(psychologist.id(), request.startsAt(), request.endsAt())) {
            throw new BusinessConflictException("Ya tienes una franja que se cruza con ese horario");
        }
        Slot slot = slots.save(Slot.open(psychologist.id(), request.startsAt(), request.endsAt()));
        events.publishEvent(SlotChangedEvent.created(slot));
        return SlotView.of(slot);
    }

    @Transactional
    public void delete(AuthenticatedUser psychologist, String slotId) {
        Slot slot = slots.findOwnedForUpdate(slotId, psychologist.id())
                .orElseThrow(() -> new ResourceNotFoundException("La franja ya no existe"));
        if (!slot.isAvailable()) {
            throw new BusinessConflictException(
                    "No puedes eliminar una franja reservada. Pide al paciente reprogramar o cancelar la cita.");
        }
        slots.delete(slot);
        events.publishEvent(SlotChangedEvent.deleted(slot));
    }
}

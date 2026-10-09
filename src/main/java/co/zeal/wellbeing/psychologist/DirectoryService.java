package co.zeal.wellbeing.psychologist;

import co.zeal.wellbeing.shared.error.ResourceNotFoundException;
import co.zeal.wellbeing.shared.time.DateRange;
import co.zeal.wellbeing.slot.Slot;
import co.zeal.wellbeing.slot.SlotRepository;
import co.zeal.wellbeing.slot.SlotStatus;
import co.zeal.wellbeing.slot.SlotView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** HU-04: directorio de psicólogos y sus próximos bloques, visto por los empleados. */
@Service
@Transactional(readOnly = true)
public class DirectoryService {

    static final String NOT_FOUND = "No encontramos a este psicólogo en el directorio";

    private final PsychologistRepository psychologists;
    private final SlotRepository slots;
    private final Clock clock;

    public DirectoryService(PsychologistRepository psychologists, SlotRepository slots, Clock clock) {
        this.psychologists = psychologists;
        this.slots = slots;
        this.clock = clock;
    }

    /** Quien tiene un bloque libre antes aparece primero; quien no tiene ninguno, al final. */
    public List<PsychologistView> directory() {
        Map<String, Slot> nextFree = slots.findNextFreeSlots(Instant.now(clock)).stream()
                .collect(Collectors.toMap(Slot::getPsychologistId, Function.identity(), (first, ignored) -> first));
        return psychologists.findAllWithFocusAreas().stream()
                .map(psychologist -> toView(psychologist, nextFree.get(psychologist.getId())))
                .sorted(Comparator.comparing(DirectoryService::soonestStart))
                .toList();
    }

    public PsychologistView psychologist(String id) {
        Psychologist psychologist = psychologists.findWithFocusAreas(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        Slot nextFree = slots.findFirstByPsychologistIdAndStatusAndStartsAtAfterOrderByStartsAtAsc(
                id, SlotStatus.DISPONIBLE, Instant.now(clock)).orElse(null);
        return toView(psychologist, nextFree);
    }

    /** Bloques que aún no empiezan, libres u ocupados, sin revelar quién reservó. */
    public List<SlotView> upcomingSlots(String psychologistId, DateRange range) {
        if (!psychologists.existsById(psychologistId)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return slots.findUpcomingInRange(psychologistId, Instant.now(clock), range.from(), range.toExclusive())
                .stream()
                .map(SlotView::of)
                .toList();
    }

    private static PsychologistView toView(Psychologist psychologist, Slot nextFree) {
        PsychologistView.TimeRange next = nextFree == null
                ? null
                : new PsychologistView.TimeRange(nextFree.getStartsAt(), nextFree.getEndsAt());
        return new PsychologistView(psychologist.getId(), psychologist.getFullName(), psychologist.getTitle(),
                psychologist.getFocusAreas(), next);
    }

    private static Instant soonestStart(PsychologistView view) {
        return view.nextFreeSlot() == null ? Instant.MAX : view.nextFreeSlot().startsAt();
    }
}

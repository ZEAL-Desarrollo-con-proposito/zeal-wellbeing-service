package co.zeal.wellbeing.psychologist;

import co.zeal.wellbeing.slot.Slot;
import co.zeal.wellbeing.slot.SlotRepository;
import co.zeal.wellbeing.slot.SlotStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/wellbeing/psicologos")
public class PsychologistController {

    private final PsychologistRepository psychologistRepository;
    private final SlotRepository slotRepository;

    public PsychologistController(PsychologistRepository psychologistRepository, SlotRepository slotRepository) {
        this.psychologistRepository = psychologistRepository;
        this.slotRepository = slotRepository;
    }

    @GetMapping
    public List<PsychologistDto> list() {
        return psychologistRepository.findAll().stream().map(this::toDto).toList();
    }

    @GetMapping("/{id}")
    public PsychologistDto getOne(@PathVariable String id) {
        return psychologistRepository.findById(id).map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/{id}/franjas")
    public List<Slot> getSlots(@PathVariable String id,
                               @RequestParam String desde,
                               @RequestParam String hasta) {
        return slotRepository.findByPsychologistIdAndStartsAtBetweenOrderByStartsAtAsc(
                id, Instant.parse(desde), Instant.parse(hasta));
    }

    private PsychologistDto toDto(Psychologist p) {
        NextFreeSlotDto nextFreeSlot = slotRepository
                .findByPsychologistIdAndStartsAtBetweenOrderByStartsAtAsc(
                        p.getId(), Instant.now(), Instant.now().plus(7, ChronoUnit.DAYS))
                .stream()
                .filter(s -> s.getStatus() == SlotStatus.DISPONIBLE)
                .min(Comparator.comparing(Slot::getStartsAt))
                .map(s -> new NextFreeSlotDto(s.getStartsAt().toString(), s.getEndsAt().toString()))
                .orElse(null);
        return new PsychologistDto(p.getId(), p.getFullName(), p.getTitle(), p.getFocusAreas(), nextFreeSlot);
    }

    public record NextFreeSlotDto(String startsAt, String endsAt) {}
    public record PsychologistDto(String id, String fullName, String title,
                                   List<String> focusAreas, NextFreeSlotDto nextFreeSlot) {}
}

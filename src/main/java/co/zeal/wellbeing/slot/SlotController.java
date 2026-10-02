package co.zeal.wellbeing.slot;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/wellbeing/franjas")
public class SlotController {

    private final SlotRepository slotRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public SlotController(SlotRepository slotRepository, RedisTemplate<String, Object> redisTemplate) {
        this.slotRepository = slotRepository;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/mias")
    public List<Slot> mySlots(@RequestParam String desde, @RequestParam String hasta, HttpServletRequest req) {
        String psychologistId = (String) req.getAttribute("userId");
        return slotRepository.findByPsychologistIdAndStartsAtBetweenOrderByStartsAtAsc(
                psychologistId, Instant.parse(desde), Instant.parse(hasta));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Slot create(@RequestBody NewSlotRequest body, HttpServletRequest req) {
        String role = (String) req.getAttribute("userRole");
        if (!"PSICOLOGO".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo los psicólogos pueden crear franjas.");
        }
        String psychologistId = (String) req.getAttribute("userId");
        Slot slot = Slot.builder()
                .id("slot-" + UUID.randomUUID().toString().substring(0, 8))
                .psychologistId(psychologistId)
                .startsAt(Instant.parse(body.startsAt()))
                .endsAt(Instant.parse(body.endsAt()))
                .status(SlotStatus.DISPONIBLE)
                .version(0L)
                .build();
        slotRepository.save(slot);
        redisTemplate.convertAndSend("events.slots", Map.of("type", "CREADA", "slot",
                Map.of("id", slot.getId(), "psychologistId", slot.getPsychologistId(),
                        "startsAt", slot.getStartsAt().toString(),
                        "endsAt", slot.getEndsAt().toString(),
                        "status", slot.getStatus().name())));
        return slot;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, HttpServletRequest req) {
        String psychologistId = (String) req.getAttribute("userId");
        Slot slot = slotRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!slot.getPsychologistId().equals(psychologistId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (slot.getStatus() == SlotStatus.RESERVADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes eliminar una franja reservada.");
        }
        slotRepository.delete(slot);
        redisTemplate.convertAndSend("events.slots", Map.of("type", "ELIMINADA", "slot",
                Map.of("id", slot.getId(), "psychologistId", slot.getPsychologistId(),
                        "startsAt", slot.getStartsAt().toString(),
                        "endsAt", slot.getEndsAt().toString(),
                        "status", slot.getStatus().name())));
    }

    public record NewSlotRequest(String startsAt, String endsAt) {}
}

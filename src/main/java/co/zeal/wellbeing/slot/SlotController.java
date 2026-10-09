package co.zeal.wellbeing.slot;

import co.zeal.wellbeing.shared.security.AuthenticatedUser;
import co.zeal.wellbeing.shared.security.CurrentUser;
import co.zeal.wellbeing.shared.time.DateRange;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/wellbeing/franjas")
@PreAuthorize("hasRole('PSICOLOGO')")
public class SlotController {

    private final SlotService slotService;
    private final ZoneId businessZone;

    public SlotController(SlotService slotService, ZoneId businessZone) {
        this.slotService = slotService;
        this.businessZone = businessZone;
    }

    @GetMapping("/mias")
    public List<SlotView> mySlots(@CurrentUser AuthenticatedUser user,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return slotService.mySlots(user, DateRange.of(desde, hasta, businessZone));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SlotView open(@CurrentUser AuthenticatedUser user, @Valid @RequestBody NewSlotRequest request) {
        return slotService.open(user, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser AuthenticatedUser user, @PathVariable String id) {
        slotService.delete(user, id);
    }
}

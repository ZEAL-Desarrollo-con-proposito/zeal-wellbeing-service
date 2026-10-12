package co.zeal.wellbeing.psychologist;

import co.zeal.wellbeing.shared.time.DateRange;
import co.zeal.wellbeing.slot.SlotView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/wellbeing/psicologos")
@PreAuthorize("hasRole('EMPLEADO')")
public class DirectoryController {

    private final DirectoryService directoryService;
    private final ZoneId businessZone;

    public DirectoryController(DirectoryService directoryService, ZoneId businessZone) {
        this.directoryService = directoryService;
        this.businessZone = businessZone;
    }

    @GetMapping
    public List<PsychologistView> directory() {
        return directoryService.directory();
    }

    @GetMapping("/{id}")
    public PsychologistView psychologist(@PathVariable String id) {
        return directoryService.psychologist(id);
    }

    @GetMapping("/{id}/franjas")
    public List<SlotView> upcomingSlots(@PathVariable String id,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return directoryService.upcomingSlots(id, DateRange.of(desde, hasta, businessZone));
    }
}

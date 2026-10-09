package co.zeal.wellbeing.reservation;

import co.zeal.wellbeing.shared.security.AuthenticatedUser;
import co.zeal.wellbeing.shared.security.CurrentUser;
import co.zeal.wellbeing.shared.time.DateRange;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/wellbeing/reservas")
public class ReservationController {

    private final BookingService bookingService;
    private final AgendaService agendaService;
    private final ZoneId businessZone;

    public ReservationController(BookingService bookingService, AgendaService agendaService, ZoneId businessZone) {
        this.bookingService = bookingService;
        this.agendaService = agendaService;
        this.businessZone = businessZone;
    }

    @GetMapping("/mias")
    @PreAuthorize("hasRole('EMPLEADO')")
    public List<AppointmentView> myAppointments(@CurrentUser AuthenticatedUser user) {
        return bookingService.myAppointments(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EMPLEADO')")
    public AppointmentView book(@CurrentUser AuthenticatedUser user, @Valid @RequestBody BookRequest request) {
        return bookingService.book(user, request.slotId());
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('EMPLEADO')")
    public AppointmentView cancel(@CurrentUser AuthenticatedUser user, @PathVariable String id) {
        return bookingService.cancel(user, id);
    }

    @GetMapping("/agenda")
    @PreAuthorize("hasRole('PSICOLOGO')")
    public List<AgendaEntryView> agenda(@CurrentUser AuthenticatedUser user,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return agendaService.agenda(user, DateRange.of(desde, hasta, businessZone));
    }
}

package co.zeal.wellbeing.reservation;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wellbeing/reservas")
public class ReservationController {

    private final BookingService bookingService;
    private final ReservationRepository reservationRepository;

    public ReservationController(BookingService bookingService, ReservationRepository reservationRepository) {
        this.bookingService = bookingService;
        this.reservationRepository = reservationRepository;
    }

    @GetMapping("/mias")
    public List<Reservation> myAppointments(HttpServletRequest req) {
        return reservationRepository.findByEmployeeIdOrderByStartsAtAsc((String) req.getAttribute("userId"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Reservation book(@RequestBody BookRequest body, HttpServletRequest req) {
        return bookingService.bookSlot(body.slotId(),
                (String) req.getAttribute("userId"),
                (String) req.getAttribute("userFullName"));
    }

    @PostMapping("/{id}/cancelar")
    public Reservation cancel(@PathVariable String id, HttpServletRequest req) {
        return bookingService.cancelAppointment(id, (String) req.getAttribute("userId"));
    }

    public record BookRequest(String slotId) {}
}

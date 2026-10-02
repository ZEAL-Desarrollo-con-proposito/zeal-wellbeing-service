package co.zeal.wellbeing.freedslot;

import co.zeal.wellbeing.reservation.BookingService;
import co.zeal.wellbeing.reservation.Reservation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wellbeing/cupos")
public class FreedSlotController {

    private final BookingService bookingService;

    public FreedSlotController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/{offerId}/tomar")
    public Reservation claim(@PathVariable String offerId, HttpServletRequest req) {
        return bookingService.claimFreedSlot(
                offerId,
                (String) req.getAttribute("userId"),
                (String) req.getAttribute("userFullName"));
    }
}

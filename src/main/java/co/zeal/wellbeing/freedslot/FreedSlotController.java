package co.zeal.wellbeing.freedslot;

import co.zeal.wellbeing.reservation.AppointmentView;
import co.zeal.wellbeing.shared.security.AuthenticatedUser;
import co.zeal.wellbeing.shared.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/wellbeing/cupos")
@PreAuthorize("hasRole('EMPLEADO')")
public class FreedSlotController {

    private final FreedSlotService freedSlotService;

    public FreedSlotController(FreedSlotService freedSlotService) {
        this.freedSlotService = freedSlotService;
    }

    @GetMapping
    public List<FreedSlotOfferView> openOffers(@CurrentUser AuthenticatedUser user) {
        return freedSlotService.openOffers(user);
    }

    @PostMapping("/{offerId}/tomar")
    public AppointmentView claim(@CurrentUser AuthenticatedUser user, @PathVariable String offerId) {
        return freedSlotService.claim(user, offerId);
    }
}

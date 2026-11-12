package co.zeal.wellbeing.room;

import co.zeal.wellbeing.shared.security.AuthenticatedUser;
import co.zeal.wellbeing.shared.security.CurrentUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wellbeing/reservas/{reservationId}/sala")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping
    public RoomView room(@CurrentUser AuthenticatedUser user, @PathVariable String reservationId) {
        return roomService.room(user, reservationId);
    }

    /** DT-7: permiso de entrada a la sala de LiveKit de la cita; 404 si no corresponde emitirlo. */
    @GetMapping("/conexion")
    public RoomView.Connection connection(@CurrentUser AuthenticatedUser user, @PathVariable String reservationId) {
        return roomService.connection(user, reservationId);
    }

    @PostMapping("/activar")
    @PreAuthorize("hasRole('PSICOLOGO')")
    public RoomView activate(@CurrentUser AuthenticatedUser user, @PathVariable String reservationId) {
        return roomService.activate(user, reservationId);
    }
}

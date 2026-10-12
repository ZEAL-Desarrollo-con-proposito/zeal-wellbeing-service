package co.zeal.wellbeing.reservation;

import co.zeal.wellbeing.room.RoomProperties;
import co.zeal.wellbeing.shared.security.AuthenticatedUser;
import co.zeal.wellbeing.shared.time.DateRange;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** HU-03: el psicólogo consulta las citas de su agenda y cuándo puede entrar a cada sala. */
@Service
public class AgendaService {

    private final ReservationRepository reservations;
    private final RoomProperties roomProperties;

    public AgendaService(ReservationRepository reservations, RoomProperties roomProperties) {
        this.reservations = reservations;
        this.roomProperties = roomProperties;
    }

    @Transactional(readOnly = true)
    public List<AgendaEntryView> agenda(AuthenticatedUser psychologist, DateRange range) {
        return reservations.findAgenda(psychologist.id(), range.from(), range.toExclusive()).stream()
                .map(this::toEntry)
                .toList();
    }

    private AgendaEntryView toEntry(Reservation reservation) {
        boolean joinable = reservation.isConfirmed();
        return new AgendaEntryView(
                reservation.getId(),
                new AgendaEntryView.Participant(reservation.getEmployeeId(), reservation.getEmployeeName()),
                reservation.getStartsAt(),
                reservation.getEndsAt(),
                reservation.getStatus(),
                joinable ? roomProperties.opensAt(reservation.getStartsAt()) : null,
                joinable ? reservation.getEndsAt() : null);
    }
}

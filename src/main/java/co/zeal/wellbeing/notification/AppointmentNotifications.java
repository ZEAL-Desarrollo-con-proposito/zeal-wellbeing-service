package co.zeal.wellbeing.notification;

import co.zeal.wellbeing.psychologist.PsychologistSummary;
import co.zeal.wellbeing.reservation.Reservation;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Redacta las notificaciones de citas igual que las muestra el frontend. */
@Component
public class AppointmentNotifications {

    static final String APPOINTMENTS_LINK = "/empleado/mis-citas";
    private static final Locale SPANISH = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", SPANISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("H:mm", SPANISH);

    private final ZoneId zone;

    public AppointmentNotifications(ZoneId businessZone) {
        this.zone = businessZone;
    }

    /** "Laura Martínez · martes, 23 de septiembre de 9:00 a 9:50". */
    public NotificationRequestedEvent confirmed(Reservation reservation, PsychologistSummary psychologist) {
        return new NotificationRequestedEvent(reservation.getEmployeeId(), "RESERVA_CONFIRMADA",
                "Tu cita quedó confirmada", psychologist.fullName() + " · " + describe(reservation),
                APPOINTMENTS_LINK);
    }

    private String describe(Reservation reservation) {
        var start = reservation.getStartsAt().atZone(zone);
        var end = reservation.getEndsAt().atZone(zone);
        return DAY.format(start).toLowerCase(SPANISH) + " de " + TIME.format(start) + " a " + TIME.format(end);
    }
}

package co.zeal.wellbeing.notification;

import co.zeal.wellbeing.reservation.Reservation;
import co.zeal.wellbeing.slot.Slot;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentNotificationsTest {

    private static final Instant START = Instant.parse("2026-10-20T14:00:00Z"); // 9:00 en Bogotá

    private final AppointmentNotifications notifications = new AppointmentNotifications(ZoneId.of("America/Bogota"));

    private Reservation reservation() {
        Slot slot = Slot.open("usr-psicologo-001", START, START.plus(Duration.ofMinutes(50)));
        return Reservation.confirm(slot, "usr-empleado-001", "David Cajamarca", START.minus(Duration.ofDays(5)));
    }

    @Test
    void psychologistIsNotifiedOfANewAppointment() {
        NotificationRequestedEvent event = notifications.bookedForPsychologist(reservation());

        assertThat(event.userId()).isEqualTo("usr-psicologo-001");
        assertThat(event.type()).isEqualTo("NUEVA_CITA");
        assertThat(event.message()).isEqualTo("David Cajamarca · martes, 20 de octubre de 9:00 a 9:50");
        assertThat(event.link()).isEqualTo("/psicologo/agenda");
    }

    @Test
    void psychologistIsNotifiedOfACancellation() {
        NotificationRequestedEvent event = notifications.cancelledForPsychologist(reservation());

        assertThat(event.userId()).isEqualTo("usr-psicologo-001");
        assertThat(event.type()).isEqualTo("CITA_CANCELADA");
        assertThat(event.message()).startsWith("David Cajamarca · martes, 20 de octubre de 9:00 a 9:50");
    }
}

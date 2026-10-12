package co.zeal.wellbeing.reservation;

import co.zeal.wellbeing.psychologist.PsychologistSummary;

import java.time.Instant;

/** Cita vista por el empleado: contrato de {@code isAppointment()} del frontend. */
public record AppointmentView(String id, PsychologistSummary psychologist, Instant startsAt, Instant endsAt,
                              ReservationStatus status) {

    public static AppointmentView of(Reservation reservation, PsychologistSummary psychologist, Instant now) {
        return new AppointmentView(reservation.getId(), psychologist, reservation.getStartsAt(),
                reservation.getEndsAt(), reservation.statusAt(now));
    }
}

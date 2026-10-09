package co.zeal.wellbeing.reservation;

import java.time.Instant;

/** Cita vista por el psicólogo en su agenda: contrato de {@code isReservation()} del frontend. */
public record AgendaEntryView(String id, Participant employee, Instant startsAt, Instant endsAt,
                              ReservationStatus status, Instant joinOpensAt, Instant joinClosesAt) {

    public record Participant(String id, String fullName) {
    }
}

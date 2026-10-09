package co.zeal.wellbeing.reservation;

import co.zeal.wellbeing.slot.Slot;
import co.zeal.wellbeing.slot.SlotStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private static final Instant START = Instant.parse("2026-10-20T14:00:00Z");
    private static final Duration NOTICE = Duration.ofHours(24);

    private Reservation reservation() {
        Slot slot = Slot.open("psi", START, START.plus(Duration.ofMinutes(50)));
        return Reservation.confirm(slot, "emp", "Empleado", START.minus(Duration.ofDays(5)));
    }

    @Test
    void confirmingReservesTheSlot() {
        Slot slot = Slot.open("psi", START, START.plus(Duration.ofMinutes(50)));
        Reservation.confirm(slot, "emp", "Empleado", START);
        assertThat(slot.getStatus()).isEqualTo(SlotStatus.RESERVADA);
        assertThatThrownBy(() -> Reservation.confirm(slot, "otro", "Otro", START))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancellationWindowIsExactly24Hours() {
        Reservation reservation = reservation();
        assertThat(reservation.canBeCancelledAt(START.minus(NOTICE), NOTICE)).isTrue();
        assertThat(reservation.canBeCancelledAt(START.minus(NOTICE).plusSeconds(1), NOTICE)).isFalse();
    }

    @Test
    void confirmedReservationIsPresentedAsFinishedOnceItEnds() {
        Reservation reservation = reservation();
        assertThat(reservation.statusAt(START)).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(reservation.statusAt(START.plus(Duration.ofMinutes(50)))).isEqualTo(ReservationStatus.FINALIZADA);
    }

    @Test
    void roomActivationIsIdempotent() {
        Reservation reservation = reservation();
        assertThat(reservation.activateRoom(START)).isTrue();
        assertThat(reservation.activateRoom(START.plusSeconds(5))).isFalse();
        assertThat(reservation.isRoomActive()).isTrue();
    }
}

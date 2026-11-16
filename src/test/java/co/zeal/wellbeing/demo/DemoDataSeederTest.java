package co.zeal.wellbeing.demo;

import co.zeal.wellbeing.reservation.Reservation;
import co.zeal.wellbeing.reservation.ReservationRepository;
import co.zeal.wellbeing.shared.cluster.ClusterLock;
import co.zeal.wellbeing.slot.Slot;
import co.zeal.wellbeing.slot.SlotRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La agenda de demostración se siembra una sola vez, es futura y no se cruza (sustentación). */
class DemoDataSeederTest {

    private static final Instant NOW = Instant.parse("2026-11-11T15:07:30Z");
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final SlotRepository slots = mock(SlotRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final ClusterLock lock = mock(ClusterLock.class);
    private final DemoDataSeeder seeder = new DemoDataSeeder(slots, reservations, Clock.fixed(NOW, ZoneOffset.UTC), lock, BOGOTA);

    @Test
    void seedsAFutureNonOverlappingAgendaWithReservationsAfterTakingTheClusterLock() {
        when(slots.count()).thenReturn(0L);
        when(slots.save(any(Slot.class))).thenAnswer(call -> call.getArgument(0));

        seeder.run(null);

        InOrder order = inOrder(lock, slots);
        order.verify(lock).acquireForTransaction("demo-data:wellbeing");
        order.verify(slots).count();
        ArgumentCaptor<Slot> saved = ArgumentCaptor.forClass(Slot.class);
        verify(slots, atLeastOnce()).save(saved.capture());
        List<Slot> agenda = saved.getAllValues();
        assertThat(agenda).hasSizeGreaterThan(50);
        assertThat(agenda).allSatisfy(slot -> assertThat(slot.getStartsAt()).isAfter(NOW));
        assertThat(agenda).extracting(Slot::getStartsAt).as("la primera es la cita de demostración, en 5 min")
                .contains(NOW.truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plus(Duration.ofMinutes(5)));
        verify(reservations, atLeastOnce()).save(any(Reservation.class));
    }

    @Test
    void doesNotSeedTwiceWhenSlotsAlreadyExist() {
        when(slots.count()).thenReturn(3L);

        seeder.run(null);

        verify(lock).acquireForTransaction("demo-data:wellbeing");
        verify(slots, never()).save(any(Slot.class));
        verify(reservations, never()).save(any(Reservation.class));
    }
}

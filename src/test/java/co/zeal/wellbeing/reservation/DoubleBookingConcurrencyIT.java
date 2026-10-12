package co.zeal.wellbeing.reservation;

import co.zeal.wellbeing.shared.error.BusinessConflictException;
import co.zeal.wellbeing.slot.Slot;
import co.zeal.wellbeing.slot.SlotRepository;
import co.zeal.wellbeing.slot.SlotStatus;
import co.zeal.wellbeing.support.Concurrently;
import co.zeal.wellbeing.support.IntegrationTest;
import co.zeal.wellbeing.support.Users;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reto C-2: 100 empleados reservan la misma franja al mismo tiempo; solo uno lo logra. */
class DoubleBookingConcurrencyIT extends IntegrationTest {

    private static final int EMPLOYEES = 100;

    @Autowired
    BookingService bookingService;
    @Autowired
    SlotRepository slots;
    @Autowired
    ReservationRepository reservations;

    @Test
    void onlyOneEmployeeBooksAContestedSlot() throws Exception {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Slot slot = slots.save(Slot.open(Users.LAURA.id(), start, start.plus(Duration.ofMinutes(50))));

        List<Concurrently.Outcome<AppointmentView>> outcomes = Concurrently.run(EMPLOYEES,
                i -> bookingService.book(Users.employee("c2-" + i), slot.getId()));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .hasSize(EMPLOYEES - 1)
                .allSatisfy(outcome -> assertThat(outcome.error()).isInstanceOf(BusinessConflictException.class));
        assertThat(reservations.findAll()).filteredOn(r -> r.getSlotId().equals(slot.getId())).hasSize(1);
        assertThat(slots.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.RESERVADA);
    }
}

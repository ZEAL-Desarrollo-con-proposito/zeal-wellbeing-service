package co.zeal.wellbeing.freedslot;

import co.zeal.wellbeing.reservation.AppointmentView;
import co.zeal.wellbeing.reservation.BookingService;
import co.zeal.wellbeing.reservation.ReservationRepository;
import co.zeal.wellbeing.reservation.ReservationStatus;
import co.zeal.wellbeing.shared.error.BusinessConflictException;
import co.zeal.wellbeing.shared.events.IntegrationEvent;
import co.zeal.wellbeing.slot.Slot;
import co.zeal.wellbeing.slot.SlotRepository;
import co.zeal.wellbeing.support.Concurrently;
import co.zeal.wellbeing.support.IntegrationTest;
import co.zeal.wellbeing.support.Users;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Reto C-3: al cancelarse una cita, quienes tienen una cita posterior ese día reciben el cupo y
 * compiten por él a la vez. Exactamente uno lo toma; el resto recibe 409.
 */
class FreedSlotRaceConcurrencyIT extends IntegrationTest {

    private static final int CANDIDATES = 50;

    @Autowired
    BookingService bookingService;
    @Autowired
    FreedSlotService freedSlotService;
    @Autowired
    FreedSlotOfferRepository offers;
    @Autowired
    SlotRepository slots;
    @Autowired
    ReservationRepository reservations;
    @Autowired
    ZoneId businessZone;

    @Test
    void exactlyOneCandidateTakesTheFreedSlot() throws Exception {
        LocalDate day = LocalDate.now(businessZone).plusDays(5);
        Instant freedStart = day.atTime(LocalTime.of(7, 0)).atZone(businessZone).toInstant();
        Slot freed = slots.save(Slot.open(Users.LAURA.id(), freedStart, freedStart.plus(Duration.ofMinutes(50))));
        AppointmentView cancelled = bookingService.book(Users.employee("c3-releaser"), freed.getId());

        Instant laterStart = day.atTime(LocalTime.of(12, 0)).atZone(businessZone).toInstant();
        for (int i = 0; i < CANDIDATES; i++) {
            Instant start = laterStart.plus(Duration.ofMinutes(5L * i));
            Slot later = slots.save(Slot.open(Users.LAURA.id(), start, start.plus(Duration.ofMinutes(5))));
            bookingService.book(Users.employee("c3-" + i), later.getId());
        }

        bookingService.cancel(Users.employee("c3-releaser"), cancelled.id());

        ArgumentCaptor<IntegrationEvent> published = ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(published.capture());
        FreedSlotOfferedEvent offered = published.getAllValues().stream()
                .filter(FreedSlotOfferedEvent.class::isInstance)
                .map(FreedSlotOfferedEvent.class::cast)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(offered.recipientIds()).hasSize(CANDIDATES).doesNotContain("c3-releaser");

        List<Concurrently.Outcome<AppointmentView>> outcomes = Concurrently.run(CANDIDATES,
                i -> freedSlotService.claim(Users.employee("c3-" + i), offered.offer().id()));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allSatisfy(outcome -> assertThat(outcome.error()).isInstanceOf(BusinessConflictException.class));
        assertThat(reservations.findAll())
                .filteredOn(r -> r.getSlotId().equals(freed.getId()) && r.getStatus() == ReservationStatus.CONFIRMADA)
                .hasSize(1);
        assertThat(offers.findById(offered.offer().id()).orElseThrow().isTaken()).isTrue();
    }
}

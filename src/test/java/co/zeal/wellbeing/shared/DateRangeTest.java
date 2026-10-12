package co.zeal.wellbeing.shared;

import co.zeal.wellbeing.shared.error.BusinessRuleViolationException;
import co.zeal.wellbeing.shared.time.DateRange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Test
    void coversWholeLocalDaysInTheBusinessZone() {
        DateRange range = DateRange.of(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12), BOGOTA);
        assertThat(range.from()).isEqualTo(Instant.parse("2026-10-12T05:00:00Z"));
        assertThat(range.toExclusive()).isEqualTo(Instant.parse("2026-10-13T05:00:00Z"));
    }

    @Test
    void rejectsInvertedRanges() {
        assertThatThrownBy(() -> DateRange.of(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 11), BOGOTA))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}

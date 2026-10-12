package co.zeal.wellbeing.shared.time;

import co.zeal.wellbeing.shared.error.BusinessRuleViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Rango {@code ?desde=AAAA-MM-DD&hasta=AAAA-MM-DD} (ambos inclusive) convertido a instantes en la
 * zona del negocio: {@code [inicio de desde, inicio del día siguiente a hasta)}.
 */
public record DateRange(Instant from, Instant toExclusive) {

    static final int MAX_DAYS = 62;

    public static DateRange of(LocalDate from, LocalDate to, ZoneId zone) {
        if (to.isBefore(from)) {
            throw new BusinessRuleViolationException("La fecha 'hasta' no puede ser anterior a 'desde'.");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new BusinessRuleViolationException("El rango de fechas no puede superar " + MAX_DAYS + " días.");
        }
        return new DateRange(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
    }
}

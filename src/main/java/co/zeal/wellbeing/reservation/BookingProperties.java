package co.zeal.wellbeing.reservation;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Reglas configurables de la agenda: antelación mínima para cancelar (HU-07) y cuánto dura la
 * oferta de un cupo liberado (HU-08).
 */
@Validated
@ConfigurationProperties("app.booking")
public record BookingProperties(@NotNull Duration cancellationNotice, @NotNull Duration freedSlotOfferWindow) {
}

package co.zeal.wellbeing.slot;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record NewSlotRequest(
        @NotNull(message = "La hora de fin debe ser posterior a la de inicio") Instant startsAt,
        @NotNull(message = "La hora de fin debe ser posterior a la de inicio") Instant endsAt) {
}

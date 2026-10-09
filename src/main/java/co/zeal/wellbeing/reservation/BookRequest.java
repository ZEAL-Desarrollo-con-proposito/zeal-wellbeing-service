package co.zeal.wellbeing.reservation;

import jakarta.validation.constraints.NotBlank;

public record BookRequest(@NotBlank(message = "Este bloque ya no existe. Elige otro.") String slotId) {
}

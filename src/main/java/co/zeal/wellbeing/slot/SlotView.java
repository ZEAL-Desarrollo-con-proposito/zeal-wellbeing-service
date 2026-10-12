package co.zeal.wellbeing.slot;

import java.time.Instant;

/** Contrato de {@code isSlot()} del frontend. */
public record SlotView(String id, String psychologistId, Instant startsAt, Instant endsAt, SlotStatus status) {

    public static SlotView of(Slot slot) {
        return new SlotView(slot.getId(), slot.getPsychologistId(), slot.getStartsAt(), slot.getEndsAt(),
                slot.getStatus());
    }
}

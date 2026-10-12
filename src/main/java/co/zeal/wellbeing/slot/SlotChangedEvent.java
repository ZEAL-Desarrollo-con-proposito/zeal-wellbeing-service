package co.zeal.wellbeing.slot;

import co.zeal.wellbeing.shared.events.Channels;
import co.zeal.wellbeing.shared.events.IntegrationEvent;

/** RT-2: cambio en una franja; el gateway lo difunde tal cual por {@code /topic/franjas}. */
public record SlotChangedEvent(Type type, SlotView slot) implements IntegrationEvent {

    public enum Type {
        CREADA,
        ELIMINADA,
        ACTUALIZADA
    }

    public static SlotChangedEvent created(Slot slot) {
        return new SlotChangedEvent(Type.CREADA, SlotView.of(slot));
    }

    public static SlotChangedEvent deleted(Slot slot) {
        return new SlotChangedEvent(Type.ELIMINADA, SlotView.of(slot));
    }

    public static SlotChangedEvent updated(Slot slot) {
        return new SlotChangedEvent(Type.ACTUALIZADA, SlotView.of(slot));
    }

    @Override
    public String channel() {
        return Channels.SLOTS;
    }
}

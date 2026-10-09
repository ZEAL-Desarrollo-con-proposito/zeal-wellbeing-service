package co.zeal.wellbeing.freedslot;

import co.zeal.wellbeing.shared.events.Channels;
import co.zeal.wellbeing.shared.events.IntegrationEvent;

import java.util.List;

/** RT-2: el gateway entrega la oferta en la cola privada de cada destinatario. */
public record FreedSlotOfferedEvent(FreedSlotOfferView offer, List<String> recipientIds) implements IntegrationEvent {

    @Override
    public String channel() {
        return Channels.FREED_SLOT_OFFERED;
    }
}

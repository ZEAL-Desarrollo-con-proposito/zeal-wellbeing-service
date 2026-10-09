package co.zeal.wellbeing.freedslot;

import co.zeal.wellbeing.shared.events.Channels;
import co.zeal.wellbeing.shared.events.IntegrationEvent;

import java.util.List;

/** El cupo ya tiene dueño: el gateway avisa a los demás destinatarios para que lo retiren. */
public record FreedSlotTakenEvent(String offerId, String winnerId, List<String> recipientIds)
        implements IntegrationEvent {

    @Override
    public String channel() {
        return Channels.FREED_SLOT_TAKEN;
    }
}

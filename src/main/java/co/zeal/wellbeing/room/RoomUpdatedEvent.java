package co.zeal.wellbeing.room;

import co.zeal.wellbeing.shared.events.Channels;
import co.zeal.wellbeing.shared.events.IntegrationEvent;

import java.util.List;

/** RT-3: la sala cambió; el gateway avisa a ambos participantes por {@code /user/queue/salas}. */
public record RoomUpdatedEvent(String reservationId, List<String> participantIds) implements IntegrationEvent {

    @Override
    public String channel() {
        return Channels.ROOM_UPDATED;
    }
}

package co.zeal.wellbeing.notification;

import co.zeal.wellbeing.shared.events.Channels;
import co.zeal.wellbeing.shared.events.IntegrationEvent;

/**
 * Pide al servicio de notificaciones (Real-Time Gateway) que guarde y entregue en vivo un aviso
 * a una persona. {@code link} es una ruta interna del frontend.
 */
public record NotificationRequestedEvent(String userId, String type, String title, String message, String link)
        implements IntegrationEvent {

    @Override
    public String channel() {
        return Channels.NOTIFICATIONS;
    }
}

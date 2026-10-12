package co.zeal.wellbeing.shared.events;

/**
 * Evento que sale del servicio hacia el Message Broker (EDA). Cada evento sabe en qué canal se
 * publica; su contenido se serializa como JSON plano, sin metadatos de clases Java.
 */
public interface IntegrationEvent {

    String channel();
}

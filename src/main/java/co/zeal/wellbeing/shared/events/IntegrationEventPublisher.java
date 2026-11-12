package co.zeal.wellbeing.shared.events;

/** Puerto de salida hacia el Message Broker. */
public interface IntegrationEventPublisher {

    void publish(IntegrationEvent event);

    /** Publica indicando la hora del commit de la transacción que originó el evento (R2-24). */
    default void publish(IntegrationEvent event, java.time.Instant committedAt) {
        publish(event);
    }
}

package co.zeal.wellbeing.shared.events;

/** Puerto de salida hacia el Message Broker. */
public interface IntegrationEventPublisher {

    void publish(IntegrationEvent event);
}

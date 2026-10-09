package co.zeal.wellbeing.shared.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Los servicios registran sus eventos con {@code ApplicationEventPublisher} dentro de la
 * transacción; aquí salen al broker solo después del commit. Así nunca se anuncia un cambio que
 * terminó en rollback, ni llega un aviso antes de que el dato sea visible en la base.
 */
@Component
public class AfterCommitEventRelay {

    private static final Logger log = LoggerFactory.getLogger(AfterCommitEventRelay.class);

    private final IntegrationEventPublisher publisher;

    public AfterCommitEventRelay(IntegrationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void relay(IntegrationEvent event) {
        try {
            publisher.publish(event);
        } catch (RuntimeException e) {
            // El cambio ya está confirmado: un fallo del broker no debe convertir el éxito en error.
            log.error("No se pudo publicar el evento {} en el canal {}", event.getClass().getSimpleName(),
                    event.channel(), e);
        }
    }
}

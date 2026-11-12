package co.zeal.wellbeing.shared.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.MDC;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Publica en Redis Pub/Sub el evento como JSON plano (contrato estable entre servicios).
 *
 * <p>R2-24 / R2-29: a cada evento se le agregan, sin tocar los campos existentes (retrocompatible):
 * {@code eventId} (único: los consumidores deduplican con él), {@code commitTimestamp} (hora del commit
 * de la transacción que lo originó: el gateway mide la latencia hasta la entrega por STOMP) y
 * {@code correlationId} (traza de punta a punta desde NGINX).
 */
@Component
public class RedisIntegrationEventPublisher implements IntegrationEventPublisher {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisIntegrationEventPublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(IntegrationEvent event) {
        publish(event, Instant.now());
    }

    @Override
    public void publish(IntegrationEvent event, Instant committedAt) {
        try {
            ObjectNode json = objectMapper.valueToTree(event);
            json.put("eventId", UUID.randomUUID().toString());
            json.put("commitTimestamp", committedAt.toString());
            String correlationId = MDC.get("correlationId");
            if (correlationId != null) {
                json.put("correlationId", correlationId);
            }
            redis.convertAndSend(event.channel(), objectMapper.writeValueAsString(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + event.getClass().getSimpleName(), e);
        }
    }
}

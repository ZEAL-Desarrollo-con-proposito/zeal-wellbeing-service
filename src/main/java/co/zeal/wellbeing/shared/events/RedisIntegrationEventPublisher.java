package co.zeal.wellbeing.shared.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Publica en Redis Pub/Sub el evento como JSON plano (contrato estable entre servicios). */
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
        try {
            redis.convertAndSend(event.channel(), objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el evento " + event.getClass().getSimpleName(), e);
        }
    }
}

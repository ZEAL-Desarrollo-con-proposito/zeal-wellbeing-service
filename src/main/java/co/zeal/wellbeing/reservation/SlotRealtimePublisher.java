package co.zeal.wellbeing.reservation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
public class SlotRealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(SlotRealtimePublisher.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public SlotRealtimePublisher(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(SlotRealtimeEvent event) {
        try {
            Object payload = Map.of("type", event.type(), "slot", event.slot());
            redisTemplate.convertAndSend("events.slots", payload);
        } catch (RuntimeException exception) {
            log.error("No se pudo publicar la actualización de la franja {}", event.slot().get("id"), exception);
        }
    }
}

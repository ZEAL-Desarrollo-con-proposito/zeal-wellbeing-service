package co.zeal.wellbeing.shared.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * R2-9 / R2-14 · El Real-Time Gateway no tiene base de datos: publica sus eventos de seguridad
 * (CONNECT rechazado, suscripción ajena) en Redis y aquí se insertan en {@code security_audit}.
 * Todas las réplicas de wellbeing reciben cada evento; lo inserta solo la que reclama su
 * {@code eventId} en Redis ({@code SET NX}).
 */
@Configuration
public class GatewayAuditListener implements MessageListener {

    public static final String CHANNEL = "zeal.security.audit";
    private static final Logger log = LoggerFactory.getLogger(GatewayAuditListener.class);

    private final SecurityAudit audit;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;

    public GatewayAuditListener(SecurityAudit audit, ObjectMapper objectMapper, StringRedisTemplate redis) {
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.redis = redis;
    }

    @Bean
    RedisMessageListenerContainer securityAuditListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(this, new ChannelTopic(CHANNEL));
        return container;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode event = objectMapper.readTree(message.getBody());
            String eventId = text(event, "eventId");
            if (eventId != null && !Boolean.TRUE.equals(redis.opsForValue()
                    .setIfAbsent("zeal:audit-claims:" + eventId, "1", Duration.ofMinutes(5)))) {
                return; // Otra réplica ya lo registró.
            }
            audit.record(text(event, "service"), text(event, "eventType"),
                    SecurityAudit.Outcome.valueOf(text(event, "outcome")), text(event, "userId"), text(event, "ip"),
                    text(event, "correlationId"), text(event, "detail"));
        } catch (Exception e) {
            log.error("Evento de auditoría del gateway inválido: {}", e.getMessage());
        }
    }

    private static String text(JsonNode event, String field) {
        JsonNode value = event.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}

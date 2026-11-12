package co.zeal.wellbeing.shared.cache;

import co.zeal.wellbeing.psychologist.PsychologistView;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * R2-26 · Directorio de psicólogos (HU-04) con su próximo bloque libre en Spring Cache sobre Redis (compartida por todas las réplicas).
 * Se invalida con los eventos de dominio que ya viajan por Redis Pub/Sub; el TTL es solo un respaldo
 * (por si se pierde un evento, o porque el paso del tiempo también cambia el resultado).
 *
 * <p>El valor se serializa con su tipo concreto (lista de {@code PsychologistView}), sin información de clases
 * en el JSON: nadie puede inyectar un tipo arbitrario a deserializar.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE = "psychologist-directory";

    @Bean
    CacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper,
                              @Value("${app.cache.ttl:60s}") Duration ttl) {
        var serializer = new Jackson2JsonRedisSerializer<Object>(objectMapper,
                objectMapper.getTypeFactory().constructCollectionType(List.class, PsychologistView.class));
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .prefixCacheNameWith("zeal:cache:")
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        return RedisCacheManager.builder(connectionFactory).withCacheConfiguration(CACHE, config).build();
    }

    /** Cada réplica escucha los eventos de dominio y limpia la caché compartida (idempotente). */
    @Bean
    RedisMessageListenerContainer cacheEvictionListenerContainer(RedisConnectionFactory connectionFactory,
                                                                 CacheManager cacheManager) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> Objects.requireNonNull(cacheManager.getCache(CACHE)).clear(),
                Stream.of("zeal.wellbeing.slots").map(ChannelTopic::new).toList());
        return container;
    }
}

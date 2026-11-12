package co.zeal.wellbeing.shared.cache;

import co.zeal.wellbeing.psychologist.DirectoryService;
import co.zeal.wellbeing.psychologist.PsychologistView;
import co.zeal.wellbeing.slot.Slot;
import co.zeal.wellbeing.slot.SlotRepository;
import co.zeal.wellbeing.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** R2-26 · El directorio se sirve desde Redis y se invalida con el evento de franjas. */
class DirectoryCacheIT extends IntegrationTest {

    private static final String KEY = "zeal:cache:psychologist-directory::todos";

    @Autowired
    DirectoryService directory;
    @Autowired
    SlotRepository slots;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    CacheManager caches;

    @BeforeEach
    void emptyCache() {
        caches.getCache(CacheConfig.CACHE).clear();
    }

    @Test
    void theDirectoryIsCachedInRedisAndRefreshedBySlotEvents() throws Exception {
        List<PsychologistView> first = directory.directory();
        assertThat(redis.hasKey(KEY)).isTrue();

        Instant soon = Instant.now().plus(1, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        slots.save(Slot.open("usr-psicologo-004", soon, soon.plus(Duration.ofMinutes(50))));
        assertThat(directory.directory()).as("mientras no llega el evento, se sirve la caché").isEqualTo(first);

        redis.convertAndSend("zeal.wellbeing.slots", "{\"type\":\"CREADA\"}");
        long deadline = System.currentTimeMillis() + 3000;
        while (Boolean.TRUE.equals(redis.hasKey(KEY)) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }

        List<PsychologistView> refreshed = directory.directory();
        assertThat(refreshed.getFirst().id()).as("quien tiene el bloque más próximo va primero")
                .isEqualTo("usr-psicologo-004");
        assertThat(refreshed.getFirst().nextFreeSlot().startsAt()).isEqualTo(soon);
    }
}

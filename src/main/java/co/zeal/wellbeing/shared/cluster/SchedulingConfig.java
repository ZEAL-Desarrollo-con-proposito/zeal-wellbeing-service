package co.zeal.wellbeing.shared.cluster;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * R2-1: con varias réplicas, cada tarea programada ({@code @Scheduled} + {@code @SchedulerLock}) la
 * ejecuta una sola. El bloqueo vive en la tabla {@code shedlock} de esta base y usa la hora de
 * PostgreSQL, no la de cada réplica (sus relojes pueden diferir).
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulingConfig {

    @Bean
    LockProvider lockProvider(JdbcTemplate jdbc) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbc)
                .withTableName("wellbeing.shedlock")
                .usingDbTime()
                .build());
    }
}

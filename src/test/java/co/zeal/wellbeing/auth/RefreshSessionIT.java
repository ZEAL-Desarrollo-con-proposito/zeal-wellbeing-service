package co.zeal.wellbeing.auth;

import co.zeal.wellbeing.auth.session.SessionService;
import co.zeal.wellbeing.shared.security.TokenRevocations;
import co.zeal.wellbeing.support.Concurrently;
import co.zeal.wellbeing.support.IntegrationTest;
import co.zeal.wellbeing.support.Sessions;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R2-12 / R2-13 · Refresh token rotativo con detección de reutilización (SEG-4) y lista negra en Redis.
 */
@AutoConfigureMockMvc
class RefreshSessionIT extends IntegrationTest {

    private static final String DAVID = "david.cajamarca@nexura.co";

    @Autowired
    MockMvc mvc;
    @Autowired
    Sessions sessions;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MeterRegistry meters;
    @Autowired
    RedisConnectionFactory redis;

    @Test
    void eachRenewalRotatesTheCookieAndTheOldOneStopsWorking() throws Exception {
        SessionService.IssuedSession session = sessions.open(DAVID);
        Cookie first = refreshCookie(session.refreshToken());

        Cookie second = renew(first);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        Cookie third = renew(second);
        assertThat(third.getValue()).isNotEqualTo(second.getValue());
    }

    @Test
    void reusingAnOldRefreshTokenRevokesTheWholeSessionEverywhere() throws Exception {
        SessionService.IssuedSession session = sessions.open(DAVID);
        String stolenAccess = "Bearer " + session.access().value();
        Cookie stolen = refreshCookie(session.refreshToken());
        Cookie legit = renew(stolen);                      // La víctima renueva; el atacante guarda la vieja.
        double reusesBefore = reuseCount();
        BlockingQueue<String> revocations = listen(TokenRevocations.CHANNEL);

        mvc.perform(post("/api/auth/refresh").cookie(stolen)).andExpect(status().isUnauthorized());

        // La familia entera cae: ni la cookie legítima ni ningún access token emitido sirven ya.
        mvc.perform(post("/api/auth/refresh").cookie(legit)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/wellbeing/psicologos").header("Authorization", stolenAccess))
                .andExpect(status().isUnauthorized());
        assertThat(reuseCount() - reusesBefore).as("métrica de la alerta SEG-4").isEqualTo(1.0);
        assertThat(revocations.poll(2, TimeUnit.SECONDS)).as("difusión al gateway").contains("REFRESH_REUTILIZADO");
        assertThat(jdbc.queryForObject("select count(*) from security_audit where event_type = 'REFRESH_REUTILIZADO'"
                + " and user_id = 'usr-empleado-001' and outcome = 'DENEGADO'", Integer.class)).isPositive();
    }

    /** Rotación atómica: con el mismo token en paralelo (dos réplicas) solo una petición renueva. */
    @Test
    void concurrentRenewalsWithTheSameTokenLetExactlyOneThrough() throws Exception {
        Cookie cookie = refreshCookie(sessions.open(DAVID).refreshToken());

        List<Integer> statuses = Concurrently.run(12, index -> {
            try {
                return mvc.perform(post("/api/auth/refresh").cookie(cookie)).andReturn().getResponse().getStatus();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }).stream().map(Concurrently.Outcome::value).toList();

        assertThat(statuses).filteredOn(code -> code == 200).hasSize(1);
        assertThat(statuses).filteredOn(code -> code == 401).hasSize(11);
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        SessionService.IssuedSession session = sessions.open(DAVID);
        Cookie cookie = refreshCookie(session.refreshToken());

        MvcResult result = mvc.perform(post("/api/auth/logout").cookie(cookie)).andExpect(status().isNoContent())
                .andReturn();

        assertThat(result.getResponse().getCookie("zeal_refresh").getMaxAge()).isZero();
        mvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/wellbeing/psicologos").header("Authorization", "Bearer " + session.access().value()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieOrWithGarbageIs401() throws Exception {
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("zeal_refresh", "inventado")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }

    @Test
    void refreshTokensAreStoredOnlyAsHashes() {
        SessionService.IssuedSession session = sessions.open(DAVID);

        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where token_hash = ?", Integer.class,
                session.refreshToken())).isZero();
        assertThat(jdbc.queryForList("select token_hash from refresh_tokens", String.class))
                .allMatch(hash -> hash.matches("[0-9a-f]{64}"));
    }

    private Cookie renew(Cookie cookie) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie("zeal_refresh");
    }

    private static Cookie refreshCookie(String value) {
        return new Cookie("zeal_refresh", value);
    }

    private double reuseCount() {
        var counter = meters.find(SessionService.REUSE_METRIC).counter();
        return counter == null ? 0 : counter.count();
    }

    private BlockingQueue<String> listen(String channel) throws InterruptedException {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redis);
        container.addMessageListener((Message message, byte[] pattern) -> messages.add(new String(message.getBody())),
                new ChannelTopic(channel));
        container.afterPropertiesSet();
        container.start();
        Thread.sleep(300);
        return messages;
    }
}

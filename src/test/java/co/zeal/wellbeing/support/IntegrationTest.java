package co.zeal.wellbeing.support;

import co.zeal.wellbeing.shared.events.IntegrationEventPublisher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Base de las pruebas de integración: PostgreSQL real (los bloqueos FOR UPDATE no se pueden
 * simular en memoria), Redis real (lista negra de tokens, R2-13), el proveedor OIDC de prueba
 * (mock-oauth2-server, en lugar de Microsoft Entra ID) y el broker de eventos reemplazado por un doble
 * para inspeccionar los eventos.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @ServiceConnection
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    public static final GenericContainer<?> OIDC = new GenericContainer<>("ghcr.io/navikt/mock-oauth2-server:2.3.0")
            .withEnv("JSON_CONFIG", "{\"interactiveLogin\":true}")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/microsoft/.well-known/openid-configuration"));

    static {
        POSTGRES.start();
        REDIS.start();
        OIDC.start();
    }

    @DynamicPropertySource
    static void oidc(DynamicPropertyRegistry registry) {
        registry.add("app.auth.oidc.issuer-uri", IntegrationTest::oidcIssuer);
        registry.add("app.auth.frontend-url", () -> "https://zeal.test");
    }

    public static String oidcIssuer() {
        return "http://" + OIDC.getHost() + ":" + OIDC.getMappedPort(8080) + "/microsoft";
    }

    @MockitoBean
    protected IntegrationEventPublisher eventPublisher;
}

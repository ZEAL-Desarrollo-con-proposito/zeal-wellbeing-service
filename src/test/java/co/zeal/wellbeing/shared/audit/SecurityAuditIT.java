package co.zeal.wellbeing.shared.audit;

import co.zeal.wellbeing.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R2-14 · {@code security_audit} es de solo inserción: el usuario de la aplicación no tiene UPDATE,
 * DELETE ni TRUNCATE, y un trigger lo impide incluso al propietario del esquema.
 */
class SecurityAuditIT extends IntegrationTest {

    /** Rol de aplicación que crea la migración V5 (placeholder appUser; en pruebas "zeal_user"). */
    private static final String APP_USER = "zeal_user";
    private static final String APP_PASSWORD = "zeal_password";

    @Autowired
    SecurityAudit audit;
    @Autowired
    JdbcTemplate owner;

    @Test
    void theApplicationUserCanOnlyInsertAndRead() throws Exception {
        audit.record("PRUEBA", SecurityAudit.Outcome.EXITO, "usr-prueba", "registro de prueba");

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP_USER, APP_PASSWORD);
             Statement sql = app.createStatement()) {
            sql.execute("set search_path to wellbeing");
            sql.execute("insert into security_audit (service, event_type, outcome) values ('prueba', 'PRUEBA', 'EXITO')");
            assertThat(sql.executeQuery("select count(*) from security_audit").next()).isTrue();
            assertThatThrownBy(() -> sql.execute("update security_audit set outcome = 'FALLO'"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> sql.execute("delete from security_audit"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> sql.execute("truncate security_audit"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
            assertThatThrownBy(() -> sql.execute("drop table security_audit"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("must be owner");
        }
    }

    @Autowired
    org.springframework.data.redis.core.StringRedisTemplate redis;

    /** R2-9: los eventos de seguridad del gateway (sin base propia) llegan por Redis y quedan aquí. */
    @Test
    void gatewayEventsArriveThroughRedis() throws Exception {
        redis.convertAndSend(GatewayAuditListener.CHANNEL, "{\"eventId\":\"evento-prueba-1\",\"service\":\"zeal-realtime-gateway\","
                + "\"eventType\":\"STOMP_SUSCRIPCION\",\"outcome\":\"DENEGADO\",\"userId\":\"usr-otra\","
                + "\"ip\":\"192.0.2.9\",\"correlationId\":null,\"detail\":\"destino /topic/x\"}");

        long deadline = System.currentTimeMillis() + 3000;
        Integer rows = 0;
        while (System.currentTimeMillis() < deadline) { // Espera el plazo completo: debe haber UNA fila
            rows = owner.queryForObject("select count(*) from security_audit where service = 'zeal-realtime-gateway'"
                    + " and user_id = 'usr-otra' and ip = '192.0.2.9'", Integer.class);
            Thread.sleep(50);
        }
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void evenTheOwnerCannotRewriteHistory() {
        audit.record("PRUEBA", SecurityAudit.Outcome.EXITO, "usr-prueba", null);

        assertThatThrownBy(() -> owner.update("update security_audit set user_id = 'otro'"))
                .rootCause().hasMessageContaining("solo inserción");
        assertThatThrownBy(() -> owner.update("delete from security_audit"))
                .rootCause().hasMessageContaining("solo inserción");
        assertThatThrownBy(() -> owner.execute("truncate security_audit"))
                .rootCause().hasMessageContaining("solo inserción");
    }
}

package co.zeal.wellbeing.shared.cluster;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bloqueo entre réplicas para tareas de arranque (R2-1). Toma un advisory lock de PostgreSQL ligado
 * a la transacción en curso: si dos réplicas arrancan a la vez, la segunda espera a que la primera
 * confirme y luego ve sus datos (sin él, el "si no hay datos, siembra" de las dos corre en paralelo
 * y duplica la siembra). Se libera solo al terminar la transacción.
 */
@Component
public class ClusterLock {

    private final JdbcTemplate jdbc;

    public ClusterLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void acquireForTransaction(String name) {
        jdbc.query("select pg_advisory_xact_lock(hashtext(?))", resultSet -> null, name);
    }
}

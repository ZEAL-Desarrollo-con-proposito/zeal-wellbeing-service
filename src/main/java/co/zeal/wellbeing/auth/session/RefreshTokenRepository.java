package co.zeal.wellbeing.auth.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByFamilyIdAndAccessExpiresAtAfter(String familyId, Instant now);

    /**
     * Rotación atómica entre réplicas: marca el token como usado solo si nadie lo usó antes. Si dos
     * peticiones (o dos réplicas) llegan con el mismo token, PostgreSQL deja pasar a una sola: la otra
     * actualiza 0 filas y se trata como reutilización.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") String id, @Param("now") Instant now);
}

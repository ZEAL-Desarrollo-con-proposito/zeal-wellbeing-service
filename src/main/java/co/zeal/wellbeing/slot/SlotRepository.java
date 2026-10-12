package co.zeal.wellbeing.slot;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, String> {

    /**
     * Retos C-2 y C-3: SELECT ... FOR UPDATE. Las reservas concurrentes de una misma franja se
     * serializan en PostgreSQL; la primera la marca RESERVADA y las demás la ven ocupada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.id = :id")
    Optional<Slot> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.id = :id AND s.psychologistId = :psychologistId")
    Optional<Slot> findOwnedForUpdate(@Param("id") String id, @Param("psychologistId") String psychologistId);

    @Query("""
            SELECT COUNT(s) > 0 FROM Slot s
            WHERE s.psychologistId = :psychologistId AND s.startsAt < :endsAt AND s.endsAt > :startsAt
            """)
    boolean existsOverlapping(@Param("psychologistId") String psychologistId,
                              @Param("startsAt") Instant startsAt,
                              @Param("endsAt") Instant endsAt);

    @Query("""
            SELECT s FROM Slot s
            WHERE s.psychologistId = :psychologistId AND s.startsAt >= :from AND s.startsAt < :to
            ORDER BY s.startsAt
            """)
    List<Slot> findInRange(@Param("psychologistId") String psychologistId,
                           @Param("from") Instant from,
                           @Param("to") Instant to);

    @Query("""
            SELECT s FROM Slot s
            WHERE s.psychologistId = :psychologistId AND s.startsAt > :now
              AND s.startsAt >= :from AND s.startsAt < :to
            ORDER BY s.startsAt
            """)
    List<Slot> findUpcomingInRange(@Param("psychologistId") String psychologistId,
                                   @Param("now") Instant now,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);

    Optional<Slot> findFirstByPsychologistIdAndStatusAndStartsAtAfterOrderByStartsAtAsc(
            String psychologistId, SlotStatus status, Instant after);

    /** El próximo bloque libre de cada psicólogo, en una sola consulta (sin N+1). */
    @Query("""
            SELECT s FROM Slot s
            WHERE s.status = co.zeal.wellbeing.slot.SlotStatus.DISPONIBLE
              AND s.startsAt = (SELECT MIN(o.startsAt) FROM Slot o
                                WHERE o.psychologistId = s.psychologistId
                                  AND o.status = co.zeal.wellbeing.slot.SlotStatus.DISPONIBLE
                                  AND o.startsAt > :now)
            ORDER BY s.startsAt
            """)
    List<Slot> findNextFreeSlots(@Param("now") Instant now);
}

package co.zeal.wellbeing.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    /** Bloquea la cita propia: dos cancelaciones simultáneas no liberan ni ofrecen el cupo dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id AND r.employeeId = :employeeId")
    Optional<Reservation> findOwnedForUpdate(@Param("id") String id, @Param("employeeId") String employeeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") String id);

    List<Reservation> findByEmployeeIdOrderByStartsAtAsc(String employeeId);

    @Query("""
            SELECT r FROM Reservation r
            WHERE r.psychologistId = :psychologistId AND r.startsAt >= :from AND r.startsAt < :to
            ORDER BY r.startsAt
            """)
    List<Reservation> findAgenda(@Param("psychologistId") String psychologistId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to);

    @Query("""
            SELECT COUNT(r) > 0 FROM Reservation r
            WHERE r.employeeId = :employeeId
              AND r.status = co.zeal.wellbeing.reservation.ReservationStatus.CONFIRMADA
              AND r.startsAt < :endsAt AND r.endsAt > :startsAt
            """)
    boolean existsConfirmedOverlapping(@Param("employeeId") String employeeId,
                                       @Param("startsAt") Instant startsAt,
                                       @Param("endsAt") Instant endsAt);

    /**
     * Reto C-3: quienes tienen una cita confirmada con el mismo psicólogo más tarde ese mismo día
     * (sin contar a quien liberó el cupo ni a quien ya tiene otra cita a esa hora).
     */
    @Query("""
            SELECT DISTINCT r.employeeId FROM Reservation r
            WHERE r.psychologistId = :psychologistId
              AND r.status = co.zeal.wellbeing.reservation.ReservationStatus.CONFIRMADA
              AND r.employeeId <> :releasedBy
              AND r.startsAt >= :freedEndsAt AND r.startsAt < :endOfDay
              AND NOT EXISTS (SELECT 1 FROM Reservation busy
                              WHERE busy.employeeId = r.employeeId
                                AND busy.status = co.zeal.wellbeing.reservation.ReservationStatus.CONFIRMADA
                                AND busy.startsAt < :freedEndsAt AND busy.endsAt > :freedStartsAt)
            """)
    List<String> findFreedSlotRecipients(@Param("psychologistId") String psychologistId,
                                         @Param("releasedBy") String releasedBy,
                                         @Param("freedStartsAt") Instant freedStartsAt,
                                         @Param("freedEndsAt") Instant freedEndsAt,
                                         @Param("endOfDay") Instant endOfDay);

    boolean existsByEmployeeIdAndPsychologistId(String employeeId, String psychologistId);
}

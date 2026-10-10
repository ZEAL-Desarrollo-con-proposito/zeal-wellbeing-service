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

    // RETO C-2: SELECT FOR UPDATE — serializa reservas del mismo slot
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.id = :id")
    Optional<Slot> findByIdWithLock(@Param("id") String id);

    // RETO C-3: Tomar cupo liberado — solo slots DISPONIBLES
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.id = :id AND s.status = 'DISPONIBLE'")
    Optional<Slot> findAvailableWithLock(@Param("id") String id);

    List<Slot> findByPsychologistIdAndStartsAtBetweenOrderByStartsAtAsc(
            String psychologistId, Instant from, Instant to);

    List<Slot> findByPsychologistIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
            String psychologistId, Instant from, Instant until);
}

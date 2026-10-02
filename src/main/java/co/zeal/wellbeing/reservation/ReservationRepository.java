package co.zeal.wellbeing.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    List<Reservation> findByEmployeeIdOrderByStartsAtAsc(String employeeId);

    List<Reservation> findByPsychologistIdAndStartsAtBetweenOrderByStartsAtAsc(
            String psychologistId, Instant from, Instant to);

    // RETO C-3: Empleados con citas CONFIRMADAS posteriores al slot liberado en el mismo día
    @Query("""
        SELECT r.employeeId FROM Reservation r
        WHERE r.psychologistId = :psychologistId
          AND r.status = 'CONFIRMADA'
          AND r.startsAt > :freedSlotStart
          AND r.startsAt < :endOfDay
        """)
    List<String> findCandidateIds(@Param("psychologistId") String psychologistId,
                                   @Param("freedSlotStart") Instant freedSlotStart,
                                   @Param("endOfDay") Instant endOfDay);
}

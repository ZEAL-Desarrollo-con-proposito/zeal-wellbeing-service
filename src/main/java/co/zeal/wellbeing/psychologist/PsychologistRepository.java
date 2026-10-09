package co.zeal.wellbeing.psychologist;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PsychologistRepository extends JpaRepository<Psychologist, String> {

    @EntityGraph(attributePaths = "focusAreas")
    @Query("SELECT p FROM Psychologist p")
    List<Psychologist> findAllWithFocusAreas();

    @EntityGraph(attributePaths = "focusAreas")
    @Query("SELECT p FROM Psychologist p WHERE p.id = :id")
    Optional<Psychologist> findWithFocusAreas(@Param("id") String id);

    /**
     * Bloquea el perfil del psicólogo (SELECT ... FOR UPDATE) para serializar los cambios en su
     * agenda: dos franjas que se cruzan nunca se abren a la vez aunque lleguen en paralelo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Psychologist p WHERE p.id = :id")
    Optional<Psychologist> findByIdForUpdate(@Param("id") String id);
}

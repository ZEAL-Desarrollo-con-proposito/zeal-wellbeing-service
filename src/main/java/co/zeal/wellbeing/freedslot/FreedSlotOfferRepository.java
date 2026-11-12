package co.zeal.wellbeing.freedslot;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FreedSlotOfferRepository extends JpaRepository<FreedSlotOffer, String> {

    /**
     * Reto C-3: los N candidatos que pulsan "tomar" a la vez se atienden de uno en uno. R2-8: solo si la
     * oferta se le hizo a esta persona; el filtro va en la consulta (SEG-3).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM FreedSlotOffer o WHERE o.id = :id AND :userId MEMBER OF o.recipientIds")
    Optional<FreedSlotOffer> findOfferedToForUpdate(@Param("id") String id, @Param("userId") String userId);

    @Query("""
            SELECT o FROM FreedSlotOffer o
            WHERE :userId MEMBER OF o.recipientIds
              AND o.status = co.zeal.wellbeing.freedslot.FreedSlotStatus.ABIERTA
              AND o.expiresAt > :now
            ORDER BY o.startsAt
            """)
    List<FreedSlotOffer> findOpenOffersFor(@Param("userId") String userId, @Param("now") Instant now);
}

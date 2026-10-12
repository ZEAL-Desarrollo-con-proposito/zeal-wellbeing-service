package co.zeal.wellbeing.freedslot;

import co.zeal.wellbeing.shared.id.Ids;
import co.zeal.wellbeing.slot.Slot;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Cupo liberado por una cancelación y ofrecido, por tiempo limitado, a varias personas (HU-08). */
@Entity
@Table(name = "freed_slot_offers")
public class FreedSlotOffer {

    @Id
    private String id;

    @Column(name = "slot_id", nullable = false)
    private String slotId;

    @Column(name = "psychologist_id", nullable = false)
    private String psychologistId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FreedSlotStatus status;

    @Column(name = "taken_by")
    private String takenBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection
    @CollectionTable(name = "freed_slot_offer_recipients", joinColumns = @JoinColumn(name = "offer_id"))
    @Column(name = "user_id", nullable = false)
    private Set<String> recipientIds = new LinkedHashSet<>();

    @Version
    private long version;

    protected FreedSlotOffer() {
        // JPA
    }

    public static FreedSlotOffer open(Slot slot, Collection<String> recipientIds, Instant now, Duration window) {
        if (recipientIds.isEmpty()) {
            throw new IllegalArgumentException("Un cupo liberado necesita al menos un destinatario");
        }
        FreedSlotOffer offer = new FreedSlotOffer();
        offer.id = Ids.next("cupo");
        offer.slotId = slot.getId();
        offer.psychologistId = slot.getPsychologistId();
        offer.startsAt = slot.getStartsAt();
        offer.endsAt = slot.getEndsAt();
        offer.expiresAt = now.plus(window);
        offer.status = FreedSlotStatus.ABIERTA;
        offer.createdAt = now;
        offer.recipientIds.addAll(recipientIds);
        return offer;
    }

    public boolean isOfferedTo(String userId) {
        return recipientIds.contains(userId);
    }

    public boolean isTaken() {
        return status == FreedSlotStatus.TOMADA;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void markTakenBy(String userId) {
        status = FreedSlotStatus.TOMADA;
        takenBy = userId;
    }

    public String getId() {
        return id;
    }

    public String getSlotId() {
        return slotId;
    }

    public String getPsychologistId() {
        return psychologistId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public List<String> getRecipientIds() {
        return List.copyOf(recipientIds);
    }
}

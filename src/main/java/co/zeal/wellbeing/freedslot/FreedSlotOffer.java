package co.zeal.wellbeing.freedslot;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "freed_slot_offers")
public class FreedSlotOffer {
    @Id
    private String id;
    private String slotId;
    private String psychologistId;
    private String psychologistFullName;
    private String psychologistTitle;
    private Instant startsAt;
    private Instant endsAt;
    private Instant expiresAt;
    @Enumerated(EnumType.STRING)
    private FreedSlotStatus status;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "freed_slot_candidates", joinColumns = @JoinColumn(name = "offer_id"))
    @Column(name = "user_id")
    private List<String> candidateIds = new ArrayList<>();

    public FreedSlotOffer() {}
    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getSlotId() { return slotId; }
    public void setSlotId(String v) { this.slotId = v; }
    public String getPsychologistId() { return psychologistId; }
    public void setPsychologistId(String v) { this.psychologistId = v; }
    public String getPsychologistFullName() { return psychologistFullName; }
    public void setPsychologistFullName(String v) { this.psychologistFullName = v; }
    public String getPsychologistTitle() { return psychologistTitle; }
    public void setPsychologistTitle(String v) { this.psychologistTitle = v; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant v) { this.startsAt = v; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant v) { this.endsAt = v; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant v) { this.expiresAt = v; }
    public FreedSlotStatus getStatus() { return status; }
    public void setStatus(FreedSlotStatus v) { this.status = v; }
    public List<String> getCandidateIds() { return candidateIds; }
    public void setCandidateIds(List<String> v) { this.candidateIds = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private String id, slotId, psychologistId, psychologistFullName, psychologistTitle;
        private Instant startsAt, endsAt, expiresAt;
        private FreedSlotStatus status;
        private List<String> candidateIds = new ArrayList<>();
        public Builder id(String v) { this.id = v; return this; }
        public Builder slotId(String v) { this.slotId = v; return this; }
        public Builder psychologistId(String v) { this.psychologistId = v; return this; }
        public Builder psychologistFullName(String v) { this.psychologistFullName = v; return this; }
        public Builder psychologistTitle(String v) { this.psychologistTitle = v; return this; }
        public Builder startsAt(Instant v) { this.startsAt = v; return this; }
        public Builder endsAt(Instant v) { this.endsAt = v; return this; }
        public Builder expiresAt(Instant v) { this.expiresAt = v; return this; }
        public Builder status(FreedSlotStatus v) { this.status = v; return this; }
        public Builder candidateIds(List<String> v) { this.candidateIds = v; return this; }
        public FreedSlotOffer build() {
            FreedSlotOffer o = new FreedSlotOffer();
            o.id = id; o.slotId = slotId; o.psychologistId = psychologistId;
            o.psychologistFullName = psychologistFullName; o.psychologistTitle = psychologistTitle;
            o.startsAt = startsAt; o.endsAt = endsAt; o.expiresAt = expiresAt;
            o.status = status; o.candidateIds = candidateIds;
            return o;
        }
    }
}

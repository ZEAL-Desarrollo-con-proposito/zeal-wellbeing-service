package co.zeal.wellbeing.slot;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "slots")
public class Slot {
    @Id
    private String id;
    @Column(nullable = false)
    private String psychologistId;
    @Column(nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private Instant endsAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status;
    @Version
    private Long version;

    public Slot() {}
    public Slot(String id, String psychologistId, Instant startsAt, Instant endsAt, SlotStatus status, Long version) {
        this.id = id; this.psychologistId = psychologistId; this.startsAt = startsAt;
        this.endsAt = endsAt; this.status = status; this.version = version;
    }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getPsychologistId() { return psychologistId; }
    public void setPsychologistId(String v) { this.psychologistId = v; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant v) { this.startsAt = v; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant v) { this.endsAt = v; }
    public SlotStatus getStatus() { return status; }
    public void setStatus(SlotStatus v) { this.status = v; }
    public Long getVersion() { return version; }
    public void setVersion(Long v) { this.version = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private String id, psychologistId;
        private Instant startsAt, endsAt;
        private SlotStatus status;
        private Long version;
        public Builder id(String v) { this.id = v; return this; }
        public Builder psychologistId(String v) { this.psychologistId = v; return this; }
        public Builder startsAt(Instant v) { this.startsAt = v; return this; }
        public Builder endsAt(Instant v) { this.endsAt = v; return this; }
        public Builder status(SlotStatus v) { this.status = v; return this; }
        public Builder version(Long v) { this.version = v; return this; }
        public Slot build() { return new Slot(id, psychologistId, startsAt, endsAt, status, version); }
    }
}

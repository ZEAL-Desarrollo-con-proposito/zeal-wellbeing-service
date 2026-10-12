package co.zeal.wellbeing.slot;

import co.zeal.wellbeing.shared.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

/** Franja horaria que un psicólogo abre en su agenda. Solo puede tener una reserva activa. */
@Entity
@Table(name = "slots")
public class Slot {

    @Id
    private String id;

    @Column(name = "psychologist_id", nullable = false)
    private String psychologistId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status;

    @Version
    private long version;

    protected Slot() {
        // JPA
    }

    private Slot(String psychologistId, Instant startsAt, Instant endsAt) {
        this.id = Ids.next("franja");
        this.psychologistId = Objects.requireNonNull(psychologistId);
        this.startsAt = Objects.requireNonNull(startsAt);
        this.endsAt = Objects.requireNonNull(endsAt);
        this.status = SlotStatus.DISPONIBLE;
    }

    public static Slot open(String psychologistId, Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("La franja debe terminar después de empezar");
        }
        return new Slot(psychologistId, startsAt, endsAt);
    }

    public boolean isAvailable() {
        return status == SlotStatus.DISPONIBLE;
    }

    public boolean hasStartedAt(Instant instant) {
        return !startsAt.isAfter(instant);
    }

    /** Solo se invoca con la fila bloqueada y tras comprobar {@link #isAvailable()}. */
    public void reserve() {
        if (!isAvailable()) {
            throw new IllegalStateException("La franja " + id + " ya está reservada");
        }
        status = SlotStatus.RESERVADA;
    }

    public void release() {
        status = SlotStatus.DISPONIBLE;
    }

    public String getId() {
        return id;
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

    public SlotStatus getStatus() {
        return status;
    }
}

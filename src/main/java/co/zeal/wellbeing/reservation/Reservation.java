package co.zeal.wellbeing.reservation;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reservations", uniqueConstraints =
        @UniqueConstraint(name = "uk_reservations_active_slot", columnNames = "active_slot_id"))
public class Reservation {
    @Id
    private String id;
    @Column(nullable = false) private String slotId;
    @Column(name = "active_slot_id") private String activeSlotId;
    @Column(nullable = false) private String psychologistId;
    @Column(nullable = false) private String employeeId;
    @Column(nullable = false) private String employeeName;
    @Column(nullable = false) private Instant startsAt;
    @Column(nullable = false) private Instant endsAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false) private ReservationStatus status;

    public Reservation() {}
    public Reservation(String id, String slotId, String psychologistId, String employeeId,
                       String employeeName, Instant startsAt, Instant endsAt, ReservationStatus status) {
        this.id = id; this.slotId = slotId; this.psychologistId = psychologistId;
        this.employeeId = employeeId; this.employeeName = employeeName;
        this.startsAt = startsAt; this.endsAt = endsAt; this.status = status;
        this.activeSlotId = status == ReservationStatus.CONFIRMADA ? slotId : null;
    }
    public String getId() { return id; }
    public void setId(String v) { this.id = v; }
    public String getSlotId() { return slotId; }
    public void setSlotId(String v) { this.slotId = v; }
    public String getPsychologistId() { return psychologistId; }
    public void setPsychologistId(String v) { this.psychologistId = v; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String v) { this.employeeId = v; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String v) { this.employeeName = v; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant v) { this.startsAt = v; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant v) { this.endsAt = v; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus v) {
        this.status = v;
        this.activeSlotId = v == ReservationStatus.CONFIRMADA ? slotId : null;
    }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private String id, slotId, psychologistId, employeeId, employeeName;
        private Instant startsAt, endsAt;
        private ReservationStatus status;
        public Builder id(String v) { this.id = v; return this; }
        public Builder slotId(String v) { this.slotId = v; return this; }
        public Builder psychologistId(String v) { this.psychologistId = v; return this; }
        public Builder employeeId(String v) { this.employeeId = v; return this; }
        public Builder employeeName(String v) { this.employeeName = v; return this; }
        public Builder startsAt(Instant v) { this.startsAt = v; return this; }
        public Builder endsAt(Instant v) { this.endsAt = v; return this; }
        public Builder status(ReservationStatus v) { this.status = v; return this; }
        public Reservation build() {
            return new Reservation(id, slotId, psychologistId, employeeId, employeeName, startsAt, endsAt, status);
        }
    }
}

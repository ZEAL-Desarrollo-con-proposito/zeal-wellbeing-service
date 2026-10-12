package co.zeal.wellbeing.reservation;

import co.zeal.wellbeing.shared.id.Ids;
import co.zeal.wellbeing.slot.Slot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;

/** Cita entre un empleado y un psicólogo sobre una franja. */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private String id;

    @Column(name = "slot_id", nullable = false)
    private String slotId;

    @Column(name = "psychologist_id", nullable = false)
    private String psychologistId;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "room_activated_at")
    private Instant roomActivatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Reservation() {
        // JPA
    }

    /** Reserva la franja (que debe estar bloqueada y disponible) para el empleado. */
    public static Reservation confirm(Slot slot, String employeeId, String employeeName, Instant now) {
        slot.reserve();
        Reservation reservation = new Reservation();
        reservation.id = Ids.next("reserva");
        reservation.slotId = slot.getId();
        reservation.psychologistId = slot.getPsychologistId();
        reservation.employeeId = employeeId;
        reservation.employeeName = employeeName;
        reservation.startsAt = slot.getStartsAt();
        reservation.endsAt = slot.getEndsAt();
        reservation.status = ReservationStatus.CONFIRMADA;
        reservation.createdAt = now;
        return reservation;
    }

    public boolean isConfirmed() {
        return status == ReservationStatus.CONFIRMADA;
    }

    public boolean isCancelled() {
        return status == ReservationStatus.CANCELADA;
    }

    /** Una cita confirmada que ya terminó se presenta como finalizada. */
    public ReservationStatus statusAt(Instant now) {
        return isConfirmed() && !endsAt.isAfter(now) ? ReservationStatus.FINALIZADA : status;
    }

    public boolean canBeCancelledAt(Instant now, Duration minimumNotice) {
        return !Duration.between(now, startsAt).minus(minimumNotice).isNegative();
    }

    public void cancel() {
        status = ReservationStatus.CANCELADA;
    }

    public boolean isRoomActive() {
        return roomActivatedAt != null;
    }

    /** Idempotente: activar dos veces conserva la primera hora de activación. */
    public boolean activateRoom(Instant now) {
        if (roomActivatedAt != null) {
            return false;
        }
        roomActivatedAt = now;
        return true;
    }

    public boolean involves(String userId) {
        return employeeId.equals(userId) || psychologistId.equals(userId);
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

    public String getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public ReservationStatus getStatus() {
        return status;
    }
}

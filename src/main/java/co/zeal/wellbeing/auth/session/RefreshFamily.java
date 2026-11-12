package co.zeal.wellbeing.auth.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Un inicio de sesión: la cadena de refresh tokens que se van reemplazando (R2-12). */
@Entity
@Table(name = "refresh_families")
public class RefreshFamily {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private boolean remember;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    protected RefreshFamily() {
        // JPA
    }

    RefreshFamily(String id, String userId, boolean remember, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.remember = remember;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    boolean isActiveAt(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    void revoke(Instant now, String reason) {
        if (revokedAt == null) {
            revokedAt = now;
            revokedReason = reason;
        }
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public boolean isRemember() {
        return remember;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}

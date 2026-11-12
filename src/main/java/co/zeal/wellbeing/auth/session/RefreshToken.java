package co.zeal.wellbeing.auth.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Un refresh token de una familia. Solo su hash; se usa una sola vez (R2-12). */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private String id;

    @Column(name = "family_id", nullable = false)
    private String familyId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "access_jti", nullable = false)
    private String accessJti;

    @Column(name = "access_expires_at", nullable = false)
    private Instant accessExpiresAt;

    protected RefreshToken() {
        // JPA
    }

    RefreshToken(String id, String familyId, String tokenHash, Instant issuedAt, Instant expiresAt, String accessJti,
                 Instant accessExpiresAt) {
        this.id = id;
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.accessJti = accessJti;
        this.accessExpiresAt = accessExpiresAt;
    }

    public String getId() {
        return id;
    }

    public String getFamilyId() {
        return familyId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public String getAccessJti() {
        return accessJti;
    }

    public Instant getAccessExpiresAt() {
        return accessExpiresAt;
    }
}

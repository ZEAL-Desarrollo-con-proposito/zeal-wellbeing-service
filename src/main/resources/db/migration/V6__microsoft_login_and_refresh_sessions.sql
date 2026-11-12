-- R2-17 · El login se delega en Microsoft (OIDC): ZEAL ya no guarda contraseñas.
ALTER TABLE user_accounts DROP COLUMN password_hash;

-- R2-12 · Sesiones con refresh token rotativo. Una "familia" es un inicio de sesión: cada uso del
-- refresh token lo reemplaza por otro de la misma familia. Reutilizar uno ya usado revoca la familia
-- entera (SEG-4). Solo se guarda el hash SHA-256 del token, nunca el token.
CREATE TABLE refresh_families (
    id             VARCHAR(64)  PRIMARY KEY,
    user_id        VARCHAR(64)  NOT NULL REFERENCES user_accounts (id),
    remember       BOOLEAN      NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL,
    expires_at     TIMESTAMPTZ  NOT NULL,
    revoked_at     TIMESTAMPTZ,
    revoked_reason VARCHAR(40)
);
CREATE INDEX ix_refresh_families_user ON refresh_families (user_id);

CREATE TABLE refresh_tokens (
    id                VARCHAR(64) PRIMARY KEY,
    family_id         VARCHAR(64) NOT NULL REFERENCES refresh_families (id),
    token_hash        VARCHAR(64) NOT NULL UNIQUE,
    issued_at         TIMESTAMPTZ NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    used_at           TIMESTAMPTZ,
    -- Access token emitido junto con este refresh: se revoca por jti si la familia cae.
    access_jti        VARCHAR(64) NOT NULL,
    access_expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

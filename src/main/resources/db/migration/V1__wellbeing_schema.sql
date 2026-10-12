-- Esquema del Wellbeing Domain (aislado del Business Domain: vive en su propia base y esquema).

CREATE TABLE user_accounts (
    id            VARCHAR(64)  PRIMARY KEY,
    email         VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('EMPLEADO', 'PSICOLOGO', 'ADMIN')),
    organization  VARCHAR(120) NOT NULL
);

CREATE TABLE psychologists (
    id        VARCHAR(64)  PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    title     VARCHAR(120) NOT NULL,
    version   BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE psychologist_focus_areas (
    psychologist_id VARCHAR(64) NOT NULL REFERENCES psychologists (id) ON DELETE CASCADE,
    position        INTEGER     NOT NULL,
    focus_area      VARCHAR(80) NOT NULL,
    PRIMARY KEY (psychologist_id, position)
);

CREATE TABLE slots (
    id              VARCHAR(64) PRIMARY KEY,
    psychologist_id VARCHAR(64) NOT NULL REFERENCES psychologists (id),
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('DISPONIBLE', 'RESERVADA')),
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_slots_range CHECK (ends_at > starts_at)
);
CREATE INDEX ix_slots_psychologist_start ON slots (psychologist_id, starts_at);
CREATE INDEX ix_slots_status_start ON slots (status, starts_at);

CREATE TABLE reservations (
    id                VARCHAR(64)  PRIMARY KEY,
    slot_id           VARCHAR(64)  NOT NULL REFERENCES slots (id),
    psychologist_id   VARCHAR(64)  NOT NULL REFERENCES psychologists (id),
    employee_id       VARCHAR(64)  NOT NULL,
    employee_name     VARCHAR(120) NOT NULL,
    starts_at         TIMESTAMPTZ  NOT NULL,
    ends_at           TIMESTAMPTZ  NOT NULL,
    status            VARCHAR(20)  NOT NULL CHECK (status IN ('CONFIRMADA', 'CANCELADA', 'FINALIZADA')),
    room_activated_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0
);
-- Reto C-2 (defensa en profundidad): además del bloqueo pesimista, la base de datos rechaza
-- dos citas confirmadas sobre la misma franja.
CREATE UNIQUE INDEX uq_reservations_confirmed_slot ON reservations (slot_id) WHERE status = 'CONFIRMADA';
CREATE INDEX ix_reservations_employee_start ON reservations (employee_id, starts_at);
CREATE INDEX ix_reservations_psychologist_start ON reservations (psychologist_id, starts_at);

CREATE TABLE freed_slot_offers (
    id              VARCHAR(64) PRIMARY KEY,
    slot_id         VARCHAR(64) NOT NULL REFERENCES slots (id),
    psychologist_id VARCHAR(64) NOT NULL REFERENCES psychologists (id),
    starts_at       TIMESTAMPTZ NOT NULL,
    ends_at         TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    status          VARCHAR(20) NOT NULL CHECK (status IN ('ABIERTA', 'TOMADA')),
    taken_by        VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL,
    version         BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE freed_slot_offer_recipients (
    offer_id VARCHAR(64) NOT NULL REFERENCES freed_slot_offers (id) ON DELETE CASCADE,
    user_id  VARCHAR(64) NOT NULL,
    PRIMARY KEY (offer_id, user_id)
);
CREATE INDEX ix_freed_slot_offer_recipients_user ON freed_slot_offer_recipients (user_id);

CREATE TABLE chat_messages (
    id              VARCHAR(64)   PRIMARY KEY,
    conversation_id VARCHAR(140)  NOT NULL,
    sender_id       VARCHAR(64)   NOT NULL,
    sender_name     VARCHAR(120)  NOT NULL,
    text            VARCHAR(2000) NOT NULL,
    sent_at         TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_chat_messages_conversation_sent ON chat_messages (conversation_id, sent_at);

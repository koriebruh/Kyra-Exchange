-- Identity (kyra-doc/modules/01). Self-contained so the module's own tests can
-- migrate without kyra-app's V1.

CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.users (
    id                CHAR(26)    PRIMARY KEY,
    email             TEXT        NOT NULL UNIQUE,
    password_hash     TEXT        NOT NULL,
    status            VARCHAR(16) NOT NULL,
    email_verified_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE identity.email_verifications (
    id          CHAR(26)    PRIMARY KEY,
    user_id     CHAR(26)    NOT NULL REFERENCES identity.users (id),
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);
CREATE INDEX idx_email_verifications_user ON identity.email_verifications (user_id);

CREATE TABLE identity.sessions (
    id                CHAR(26)    PRIMARY KEY,
    user_id           CHAR(26)    NOT NULL REFERENCES identity.users (id),
    refresh_hash      TEXT        NOT NULL UNIQUE,
    prev_refresh_hash TEXT,
    ip                TEXT        NOT NULL,
    user_agent        TEXT        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    last_active_at    TIMESTAMPTZ NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ
);
CREATE INDEX idx_sessions_user ON identity.sessions (user_id);
CREATE INDEX idx_sessions_prev_hash ON identity.sessions (prev_refresh_hash);

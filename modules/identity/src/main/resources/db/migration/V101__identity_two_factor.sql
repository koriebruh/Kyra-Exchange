-- Two-factor auth (kyra-doc/modules/01, F3).

CREATE TABLE identity.totp_secrets (
    user_id          CHAR(26)    PRIMARY KEY REFERENCES identity.users (id),
    secret_encrypted TEXT        NOT NULL,
    enabled_at       TIMESTAMPTZ,
    last_used_step   BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE identity.recovery_codes (
    id        CHAR(26)    PRIMARY KEY,
    user_id   CHAR(26)    NOT NULL REFERENCES identity.users (id),
    code_hash TEXT        NOT NULL UNIQUE,
    used_at   TIMESTAMPTZ
);
CREATE INDEX idx_recovery_codes_user ON identity.recovery_codes (user_id);

CREATE TABLE identity.two_factor_challenges (
    id             CHAR(26)    PRIMARY KEY,
    user_id        CHAR(26)    NOT NULL REFERENCES identity.users (id),
    challenge_hash TEXT        NOT NULL UNIQUE,
    expires_at     TIMESTAMPTZ NOT NULL,
    consumed_at    TIMESTAMPTZ
);

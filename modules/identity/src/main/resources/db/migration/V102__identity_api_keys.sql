-- Programmatic HMAC API keys (kyra-doc/modules/01, F4).

CREATE TABLE identity.api_keys (
    id               CHAR(26)    PRIMARY KEY,
    key_id           TEXT        NOT NULL UNIQUE,
    user_id          CHAR(26)    NOT NULL REFERENCES identity.users (id),
    label            TEXT        NOT NULL,
    secret_encrypted TEXT        NOT NULL,
    scopes           TEXT        NOT NULL,
    ip_whitelist     TEXT        NOT NULL DEFAULT '',
    created_at       TIMESTAMPTZ NOT NULL,
    last_used_at     TIMESTAMPTZ,
    expires_at       TIMESTAMPTZ,
    revoked_at       TIMESTAMPTZ
);
CREATE INDEX idx_api_keys_user ON identity.api_keys (user_id);

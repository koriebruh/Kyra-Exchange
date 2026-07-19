-- web3j self-custody bookkeeping (kyra-doc/modules/08, DEV-INFRA.md).
-- Only populated when kyra.custody.provider=web3j; harmless otherwise.

-- One stable HD index per user -> a reproducible per-user deposit address.
CREATE SEQUENCE wallet.hd_index_seq START 1;
CREATE TABLE wallet.hd_index (
    user_id    CHAR(26)    NOT NULL PRIMARY KEY,
    idx        BIGINT      NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Idempotency: a withdrawal is broadcast at most once; retries return the
-- recorded tx hash instead of double-spending.
CREATE TABLE wallet.web3j_withdrawal (
    withdraw_id CHAR(26)    NOT NULL PRIMARY KEY,
    tx_hash     TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

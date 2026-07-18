-- Wallet (kyra-doc/modules/08). Self-contained for module tests.

CREATE SCHEMA IF NOT EXISTS wallet;

CREATE TABLE wallet.deposit_addresses (
    user_id    CHAR(26)    NOT NULL,
    asset      VARCHAR(10) NOT NULL,
    address    TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, asset)
);

CREATE TABLE wallet.deposits (
    id          CHAR(26)       PRIMARY KEY,
    user_id     CHAR(26)       NOT NULL,
    asset       VARCHAR(10)    NOT NULL,
    amount      NUMERIC(38,18) NOT NULL,
    txid        TEXT           NOT NULL UNIQUE,
    credited_at TIMESTAMPTZ    NOT NULL
);
CREATE INDEX idx_deposits_user ON wallet.deposits (user_id);

CREATE TABLE wallet.withdrawals (
    id           CHAR(26)       PRIMARY KEY,
    user_id      CHAR(26)       NOT NULL,
    asset        VARCHAR(10)    NOT NULL,
    amount       NUMERIC(38,18) NOT NULL,
    fee          NUMERIC(38,18) NOT NULL,
    to_address   TEXT           NOT NULL,
    status       VARCHAR(16)    NOT NULL,
    provider_ref TEXT,
    txid         TEXT,
    requested_at TIMESTAMPTZ    NOT NULL,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_withdrawals_user ON wallet.withdrawals (user_id);

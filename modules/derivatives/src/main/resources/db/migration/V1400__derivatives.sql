-- Perpetual futures positions (kyra-doc/modules/09 Part B). Self-contained for tests.

CREATE SCHEMA IF NOT EXISTS derivatives;

CREATE TABLE derivatives.positions (
    id               CHAR(26)       PRIMARY KEY,
    user_id          CHAR(26)       NOT NULL,
    symbol           VARCHAR(32)    NOT NULL,
    side             VARCHAR(5)     NOT NULL,
    size             NUMERIC(38,18) NOT NULL,
    entry_price      NUMERIC(38,18) NOT NULL,
    margin           NUMERIC(38,18) NOT NULL,
    collateral_asset VARCHAR(10)    NOT NULL,
    status           VARCHAR(12)    NOT NULL,
    realized_pnl     NUMERIC(38,18),
    opened_at        TIMESTAMPTZ    NOT NULL,
    closed_at        TIMESTAMPTZ
);
CREATE INDEX idx_positions_user ON derivatives.positions (user_id, status);
CREATE INDEX idx_positions_symbol ON derivatives.positions (symbol, status);

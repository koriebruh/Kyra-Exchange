-- Settlement (kyra-doc/modules/06). Self-contained for module tests.

CREATE SCHEMA IF NOT EXISTS settlement;

CREATE TABLE settlement.trades (
    id             CHAR(26)       PRIMARY KEY,
    pair           VARCHAR(21)    NOT NULL,
    base_qty       NUMERIC(38,18) NOT NULL,
    quote_amount   NUMERIC(38,18) NOT NULL,
    buyer_user_id  CHAR(26)       NOT NULL,
    seller_user_id CHAR(26)       NOT NULL,
    settled_at     TIMESTAMPTZ    NOT NULL
);
CREATE INDEX idx_trades_pair ON settlement.trades (pair, settled_at);

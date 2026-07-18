-- Market data candles (kyra-doc/modules/07). Self-contained for module tests.
-- Candles are derived from settled trades and can always be rebuilt from them.

CREATE SCHEMA IF NOT EXISTS marketdata;

CREATE TABLE marketdata.candles (
    pair         VARCHAR(21)    NOT NULL,
    interval     VARCHAR(4)     NOT NULL,
    open_time    TIMESTAMPTZ    NOT NULL,
    open         NUMERIC(38,18) NOT NULL,
    high         NUMERIC(38,18) NOT NULL,
    low          NUMERIC(38,18) NOT NULL,
    close        NUMERIC(38,18) NOT NULL,
    volume_base  NUMERIC(38,18) NOT NULL,
    volume_quote NUMERIC(38,18) NOT NULL,
    trade_count  BIGINT         NOT NULL,
    PRIMARY KEY (pair, interval, open_time)
);
CREATE INDEX idx_candles_pair_time ON marketdata.candles (pair, interval, open_time DESC);

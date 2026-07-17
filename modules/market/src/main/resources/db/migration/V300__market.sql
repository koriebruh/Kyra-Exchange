-- Market registry (kyra-doc/modules/03). Self-contained for module tests.

CREATE SCHEMA IF NOT EXISTS market;

CREATE TABLE market.assets (
    symbol            VARCHAR(10) PRIMARY KEY,
    name              TEXT        NOT NULL,
    scale             SMALLINT    NOT NULL,
    status            VARCHAR(16) NOT NULL,
    min_confirmations INT         NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE market.pairs (
    symbol          VARCHAR(21)   PRIMARY KEY,
    base_asset      VARCHAR(10)   NOT NULL REFERENCES market.assets (symbol),
    quote_asset     VARCHAR(10)   NOT NULL REFERENCES market.assets (symbol),
    tick_size       NUMERIC(38,18) NOT NULL,
    step_size       NUMERIC(38,18) NOT NULL,
    min_notional    NUMERIC(38,18) NOT NULL,
    min_qty         NUMERIC(38,18) NOT NULL,
    max_qty         NUMERIC(38,18) NOT NULL,
    max_open_orders INT           NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL
);

CREATE TABLE market.config_history (
    id          CHAR(26)    PRIMARY KEY,
    entity_type VARCHAR(16) NOT NULL,
    entity_id   TEXT        NOT NULL,
    changed_by  TEXT        NOT NULL,
    old_value   TEXT,
    new_value   TEXT        NOT NULL,
    at          TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_config_history_entity ON market.config_history (entity_type, entity_id, at);

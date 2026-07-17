-- Orders (kyra-doc/modules/04). Self-contained for module tests.

CREATE SCHEMA IF NOT EXISTS orders;

CREATE TABLE orders.orders (
    id              CHAR(26)       PRIMARY KEY,
    user_id         CHAR(26)       NOT NULL,
    client_order_id TEXT           NOT NULL,
    pair            VARCHAR(21)    NOT NULL,
    side            VARCHAR(4)     NOT NULL,
    price           NUMERIC(38,18) NOT NULL,
    qty             NUMERIC(38,18) NOT NULL,
    filled_qty      NUMERIC(38,18) NOT NULL,
    held_remaining  NUMERIC(38,18) NOT NULL,
    hold_asset      VARCHAR(10)    NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uq_order_client_id UNIQUE (user_id, client_order_id)
);
CREATE INDEX idx_orders_open ON orders.orders (pair, status)
    WHERE status IN ('OPEN', 'PARTIALLY_FILLED');
CREATE INDEX idx_orders_user ON orders.orders (user_id, created_at);

-- Freeze the fee rates onto each order at placement (kyra-doc/modules/11), so a
-- later schedule change never applies retroactively to a live order.
-- Existing rows (none in a fresh DB) default to zero.

ALTER TABLE orders.orders ADD COLUMN maker_rate NUMERIC(10,8) NOT NULL DEFAULT 0;
ALTER TABLE orders.orders ADD COLUMN taker_rate NUMERIC(10,8) NOT NULL DEFAULT 0;

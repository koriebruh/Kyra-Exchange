-- Persist the engine sequence of a resting order so the book can be rebuilt
-- with correct time priority after a restart (kyra-doc/modules/05 recovery).

ALTER TABLE orders.orders ADD COLUMN book_seq BIGINT;

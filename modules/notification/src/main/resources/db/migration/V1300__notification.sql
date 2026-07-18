-- Notification delivery log (kyra-doc/modules/13). Self-contained for tests.

CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.notifications (
    id        CHAR(26)    PRIMARY KEY,
    type      VARCHAR(32) NOT NULL,
    to_email  TEXT        NOT NULL,
    dedup_key TEXT        NOT NULL UNIQUE,
    status    VARCHAR(16) NOT NULL,
    sent_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notifications_type ON notification.notifications (type, sent_at);

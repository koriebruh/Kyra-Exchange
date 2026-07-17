-- Immutable audit log for sensitive actions (kyra-doc/modules/01, /16).
-- Append-only: an application user should hold INSERT/SELECT only (enforced by
-- DB grants in prod); a trigger blocks UPDATE/DELETE as defense in depth.

CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE audit.audit_log (
    id             CHAR(26)    PRIMARY KEY,
    actor_user_id  CHAR(26),
    action         VARCHAR(64) NOT NULL,
    target_type    VARCHAR(64),
    target_id      TEXT,
    ip             TEXT,
    detail         TEXT,
    at             TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_audit_actor ON audit.audit_log (actor_user_id, at);
CREATE INDEX idx_audit_action ON audit.audit_log (action, at);

CREATE OR REPLACE FUNCTION audit.reject_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit.audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_no_mutation
    BEFORE UPDATE OR DELETE ON audit.audit_log
    FOR EACH ROW EXECUTE FUNCTION audit.reject_mutation();

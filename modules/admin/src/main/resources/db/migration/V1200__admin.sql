-- Admin backoffice audit (kyra-doc/modules/12). Self-contained for tests.
-- Append-only: UPDATE/DELETE blocked by trigger; prod also restricts grants.

CREATE SCHEMA IF NOT EXISTS admin_ops;

CREATE TABLE admin_ops.admin_actions (
    id          CHAR(26)    PRIMARY KEY,
    admin_id    CHAR(26)    NOT NULL,
    action_type VARCHAR(48) NOT NULL,
    target_type VARCHAR(32),
    target_id   TEXT,
    reason      TEXT,
    at          TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_admin_actions_admin ON admin_ops.admin_actions (admin_id, at);
CREATE INDEX idx_admin_actions_target ON admin_ops.admin_actions (target_type, target_id);

CREATE OR REPLACE FUNCTION admin_ops.reject_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'admin_ops.admin_actions is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_admin_actions_no_mutation
    BEFORE UPDATE OR DELETE ON admin_ops.admin_actions
    FOR EACH ROW EXECUTE FUNCTION admin_ops.reject_mutation();

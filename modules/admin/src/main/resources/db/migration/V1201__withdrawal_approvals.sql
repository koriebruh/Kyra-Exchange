-- 4-eyes approvals for withdrawals (kyra-doc/modules/12). A single admin cannot
-- provide two approvals (unique withdrawal+admin).

CREATE TABLE admin_ops.withdrawal_approvals (
    id            CHAR(26)    PRIMARY KEY,
    withdrawal_id CHAR(26)    NOT NULL,
    admin_id      CHAR(26)    NOT NULL,
    approved_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_withdrawal_approver UNIQUE (withdrawal_id, admin_id)
);
CREATE INDEX idx_withdrawal_approvals_wd ON admin_ops.withdrawal_approvals (withdrawal_id);

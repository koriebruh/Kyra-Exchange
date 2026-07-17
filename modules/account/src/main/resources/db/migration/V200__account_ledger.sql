-- Account ledger (kyra-doc/modules/02). Self-contained so the module's own
-- tests can migrate without kyra-app's V1. CREATE ... IF NOT EXISTS keeps it
-- idempotent alongside V1 in the full application.

CREATE SCHEMA IF NOT EXISTS account;

CREATE TABLE account.journals (
    id          CHAR(26)    PRIMARY KEY,
    type        VARCHAR(32) NOT NULL,
    reference   TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_journal_type_ref UNIQUE (type, reference)
);

CREATE TABLE account.entries (
    id          CHAR(26)      PRIMARY KEY,
    journal_id  CHAR(26)      NOT NULL REFERENCES account.journals (id),
    account_key TEXT          NOT NULL,
    asset       VARCHAR(10)   NOT NULL,
    amount      NUMERIC(38,18) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_entries_account ON account.entries (account_key, id);
CREATE INDEX idx_entries_journal ON account.entries (journal_id);

-- Materialized running balance; last line of defense for no-negative-balance.
CREATE TABLE account.balances (
    account_key TEXT           PRIMARY KEY,
    asset       VARCHAR(10)    NOT NULL,
    amount      NUMERIC(38,18) NOT NULL,
    updated_at  TIMESTAMPTZ    NOT NULL,
    -- User accounts may never be negative; external/kyra contra accounts may.
    CONSTRAINT chk_user_balance_non_negative
        CHECK (account_key NOT LIKE 'user:%' OR amount >= 0)
);

-- entries are append-only: block UPDATE/DELETE at the DB level.
CREATE OR REPLACE FUNCTION account.reject_entry_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'account.entries is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_entries_no_update
    BEFORE UPDATE OR DELETE ON account.entries
    FOR EACH ROW EXECUTE FUNCTION account.reject_entry_mutation();

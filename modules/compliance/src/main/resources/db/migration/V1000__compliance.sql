-- Compliance (kyra-doc/modules/10). Self-contained for module tests.
-- PII (documents) is held by the KYC provider; only the standing is stored here.

CREATE SCHEMA IF NOT EXISTS compliance;

CREATE TABLE compliance.kyc_profiles (
    user_id    CHAR(26)   PRIMARY KEY,
    level      VARCHAR(4) NOT NULL,
    status     VARCHAR(16) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE compliance.kyc_submissions (
    id           CHAR(26)    PRIMARY KEY,
    user_id      CHAR(26)    NOT NULL,
    level        VARCHAR(4)  NOT NULL,
    outcome      VARCHAR(16) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_kyc_submissions_user ON compliance.kyc_submissions (user_id);

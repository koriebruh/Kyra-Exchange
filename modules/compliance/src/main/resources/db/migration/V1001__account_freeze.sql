-- Account freezes (kyra-doc/modules/12): presence of a row means the account is frozen.

CREATE TABLE compliance.account_freezes (
    user_id   CHAR(26)    PRIMARY KEY,
    reason    TEXT        NOT NULL,
    frozen_at TIMESTAMPTZ NOT NULL
);

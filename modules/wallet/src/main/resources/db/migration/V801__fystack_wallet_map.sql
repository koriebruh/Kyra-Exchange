-- Fystack per-user deposit wallet map (kyra-doc/modules/08, fystack-selfhost.md).
-- Each user gets one Fystack custody wallet (wallet_purpose=user) so deposits are
-- attributable; deposit addresses are minted per asset under that wallet. Only
-- populated when kyra.custody.provider=fystack; empty (and harmless) otherwise.
CREATE TABLE wallet.fystack_wallet (
    user_id           CHAR(26)    NOT NULL PRIMARY KEY,
    fystack_wallet_id TEXT        NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

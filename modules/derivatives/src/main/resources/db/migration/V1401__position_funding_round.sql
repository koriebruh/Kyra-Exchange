-- Track the last applied funding round per position so a round is never applied
-- twice (kyra-doc/modules/09 Part B).

ALTER TABLE derivatives.positions ADD COLUMN last_funding_round TEXT;

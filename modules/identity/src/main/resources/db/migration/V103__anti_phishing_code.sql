-- Anti-phishing code (kyra-doc/modules/01, F2b): a user phrase shown in official
-- emails so an email without it is recognisable as phishing.

ALTER TABLE identity.users ADD COLUMN anti_phishing_code TEXT;

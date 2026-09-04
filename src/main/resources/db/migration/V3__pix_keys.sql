-- A Pix key is an alias that points at an account, so money can be sent without
-- knowing the account number. "value" is a reserved word in several dialects,
-- hence key_value.
--
-- 77 characters is the longest key the BCB spec allows (an e-mail address); a
-- random key is 36, a phone 14, a CPF 11.
CREATE TABLE pix_keys (
    id         UUID        PRIMARY KEY,
    account_id UUID        NOT NULL REFERENCES accounts (id),
    type       VARCHAR(20) NOT NULL,
    key_value  VARCHAR(77) NOT NULL UNIQUE,
    created_at TIMESTAMP   NOT NULL
);

-- Listing a user's own keys is the common read; resolving someone else's goes
-- through the unique index on key_value.
CREATE INDEX idx_pix_keys_account ON pix_keys (account_id);

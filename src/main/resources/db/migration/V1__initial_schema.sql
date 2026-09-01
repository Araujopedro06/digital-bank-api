CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(80)  NOT NULL,
    email         VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL
);

CREATE TABLE accounts (
    id         UUID           PRIMARY KEY,
    user_id    UUID           NOT NULL UNIQUE REFERENCES users (id),
    number     VARCHAR(12)    NOT NULL UNIQUE,
    balance    NUMERIC(19, 2) NOT NULL DEFAULT 0,
    version    BIGINT         NOT NULL DEFAULT 0,
    created_at TIMESTAMP      NOT NULL,
    CONSTRAINT balance_non_negative CHECK (balance >= 0)
);

CREATE TABLE transactions (
    id                  UUID           PRIMARY KEY,
    account_id          UUID           NOT NULL REFERENCES accounts (id),
    type                VARCHAR(20)    NOT NULL,
    amount              NUMERIC(19, 2) NOT NULL,
    balance_after       NUMERIC(19, 2) NOT NULL,
    description         VARCHAR(120)   NOT NULL,
    counterparty_number VARCHAR(12),
    created_at          TIMESTAMP      NOT NULL,
    CONSTRAINT amount_positive CHECK (amount > 0)
);

-- The statement endpoint always filters by account and sorts by date.
CREATE INDEX idx_transactions_account_created
    ON transactions (account_id, created_at DESC);

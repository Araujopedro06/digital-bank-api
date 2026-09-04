-- A loan on the Price table: fixed instalments, each one part interest on what is
-- still owed and part repayment of it.
--
-- "outstanding" is the principal still owed, not the sum of the instalments left
-- to pay. That distinction is the whole design: it makes settling early cost
-- exactly the remaining principal, with the interest that was never accrued never
-- charged, which is what settling early is supposed to mean.
CREATE TABLE loans (
    id                 UUID           PRIMARY KEY,
    account_id         UUID           NOT NULL REFERENCES accounts (id),
    principal          NUMERIC(19, 2) NOT NULL,
    monthly_rate       NUMERIC(9, 6)  NOT NULL,
    installments       INTEGER        NOT NULL,
    installment_amount NUMERIC(19, 2) NOT NULL,
    paid_installments  INTEGER        NOT NULL DEFAULT 0,
    outstanding        NUMERIC(19, 2) NOT NULL,
    status             VARCHAR(20)    NOT NULL,
    created_at         TIMESTAMP      NOT NULL,
    settled_at         TIMESTAMP,
    CONSTRAINT loan_principal_positive CHECK (principal > 0),
    CONSTRAINT loan_installments_positive CHECK (installments > 0),
    CONSTRAINT loan_outstanding_non_negative CHECK (outstanding >= 0)
);

-- Every read is "this account's loans", and the live one is found among them.
CREATE INDEX idx_loans_account ON loans (account_id);

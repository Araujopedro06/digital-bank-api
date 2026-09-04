-- A shareable request to be paid: the thing a QR code points at.
--
-- The id is what travels in the link, and it is opaque on purpose. Putting the
-- Pix key in the URL instead would be simpler and would spread somebody's CPF or
-- phone number through chat apps, screenshots, browser history and access logs.
CREATE TABLE pix_charges (
    id          UUID           PRIMARY KEY,
    -- Giving up a key takes its charges with it: the link has nowhere to point.
    pix_key_id  UUID           NOT NULL REFERENCES pix_keys (id) ON DELETE CASCADE,
    amount      NUMERIC(19, 2),
    description VARCHAR(120),
    created_at  TIMESTAMP      NOT NULL,
    expires_at  TIMESTAMP      NOT NULL,
    CONSTRAINT charge_amount_positive CHECK (amount IS NULL OR amount > 0)
);

CREATE INDEX idx_pix_charges_key ON pix_charges (pix_key_id);

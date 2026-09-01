CREATE TABLE profile_photos (
    user_id      UUID        PRIMARY KEY REFERENCES users (id),
    content_type VARCHAR(40) NOT NULL,
    size_bytes   INTEGER     NOT NULL,
    data         BYTEA       NOT NULL,
    updated_at   TIMESTAMP   NOT NULL
);

-- Only the 128-number face descriptor is kept, never the captured image.
-- Biometric data is "dado pessoal sensível" under LGPD art. 5, II, so the
-- consent timestamp is stored alongside it and the row is deletable on request.
CREATE TABLE face_enrollments (
    user_id      UUID          PRIMARY KEY REFERENCES users (id),
    descriptor   VARCHAR(4000) NOT NULL,
    consented_at TIMESTAMP     NOT NULL,
    updated_at   TIMESTAMP     NOT NULL
);

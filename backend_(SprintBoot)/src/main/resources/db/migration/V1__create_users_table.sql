-- Users table: covers Authentication & User Dashboard workstream
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    uni_id          VARCHAR(20)     NOT NULL UNIQUE,      -- e.g. "24726476"
    email           VARCHAR(255)    NOT NULL UNIQUE,       -- registered UWA email, used for notifications
    password_hash   VARCHAR(255)    NOT NULL,              -- BCrypt hash, never store plaintext
    full_name       VARCHAR(255)    NOT NULL,
    role            VARCHAR(20)     NOT NULL DEFAULT 'STUDENT', -- STUDENT, STAFF, ADMIN
    balance_cents   BIGINT          NOT NULL DEFAULT 0,    -- stored in cents to avoid float rounding issues
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_uni_id ON users (uni_id);
CREATE INDEX idx_users_email ON users (email);

-- Seed an admin account for local dev/testing.
-- Password is "AdminPass123!" hashed with BCrypt — CHANGE before using anywhere real.
INSERT INTO users (uni_id, email, password_hash, full_name, role, balance_cents)
VALUES (
    '00000000',
    'farm.manager@uwa.edu.au',
    '$2a$10$7EqJtq98hPqEX7fNZaFWoOe6z8dNi8m5B1kA8n6b0N.dR8mYVYDVq',
    'Printer Farm Manager',
    'ADMIN',
    0
);

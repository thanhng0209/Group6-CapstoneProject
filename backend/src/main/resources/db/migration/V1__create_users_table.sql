-- Users table: covers Authentication & User Dashboard workstream
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    uni_id          VARCHAR(20)     NOT NULL UNIQUE,      -- internal ID, max 20 chars: 8-digit student number, or a short ID for staff (e.g. "LABCOORD01")
    email           VARCHAR(255)    NOT NULL UNIQUE,       -- UWA email (lower case); users can log in with it
    password_hash   VARCHAR(255)    NOT NULL,              -- BCrypt hash, never store plaintext
    full_name       VARCHAR(255)    NOT NULL,
    role            VARCHAR(20)     NOT NULL DEFAULT 'STUDENT', -- STUDENT, STAFF, ADMIN
    balance_cents   BIGINT          NOT NULL DEFAULT 0,    -- stored in cents to avoid float rounding issues
    created_at      TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_uni_id ON users (uni_id);
CREATE INDEX idx_users_email ON users (email);

-- ============================================================================
-- SEED USERS (local dev / testing only)
-- All passwords below are public in this repo: CHANGE or REMOVE them before any real use.
--
--   Admin:    00000000  or  farm.manager@uwa.edu.au          password: AdminPass123!
--   Student:  22345678  or  22345678@student.uwa.edu.au      password: StudentPass123!  ($50.00 credit)
--   Staff:    lab.coordinator@uwa.edu.au                     password: StaffPass123!
--
-- Only the ADMIN role can open /api/admin/**.
-- ============================================================================
INSERT INTO users (uni_id, email, password_hash, full_name, role, balance_cents)
VALUES
    ('00000000',
     'farm.manager@uwa.edu.au',
     '$2b$10$rKQgicKmbJ.m7ieOxu6ceOZXarcYrKe8i6Np/CgRZayHaq4gH8Ck6',
     'Printer Farm Manager',
     'ADMIN',
     0),
    ('22345678',
     '22345678@student.uwa.edu.au',
     '$2a$10$1HflLff9v2SxL7x7HoGGmuhSKr95bHWYbeC73sHel19f3Ioyz585S',
     'Test Student',
     'STUDENT',
     5000),
    ('LABCOORD01',
     'lab.coordinator@uwa.edu.au',
     '$2a$10$PZr/jcW5hONf5tgSH8hfw.P3kUoCzXBcIKqXLF0QpkMZPL2/BvT.G',
     'Lab Coordinator',
     'STAFF',
     0);

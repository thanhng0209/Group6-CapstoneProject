-- V1's seeded admin password hash did not actually correspond to "AdminPass123!"
-- (it was a placeholder typed by hand, not a real generated hash). This migration
-- corrects it with a verified BCrypt hash for "AdminPass123!" — CHANGE before any real use.
UPDATE users
SET password_hash = '$2b$10$rKQgicKmbJ.m7ieOxu6ceOZXarcYrKe8i6Np/CgRZayHaq4gH8Ck6',
    updated_at = now()
WHERE uni_id = '00000000';

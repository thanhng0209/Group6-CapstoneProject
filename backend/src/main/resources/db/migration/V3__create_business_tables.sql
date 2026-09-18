-- V3__create_business_tables.sql
-- Covers Workstream 3 (Printer Management), Workstream 5 (Job & Financial Management), and Admin Monitoring

-- ============================================================================
-- 1. PRINTERS TABLE
-- Tracks physical printer inventory, current operational status, and loaded filament.
-- ============================================================================
CREATE TABLE printers (
    id                  VARCHAR(50)     PRIMARY KEY,                    -- e.g. 'PRUSA_XL_1', 'PRUSA_MK4S_1'
    name                VARCHAR(100)    NOT NULL,                       -- Human-readable name, e.g. 'Prusa XL #1'
    model               VARCHAR(50)     NOT NULL,                       -- Canonical model ID e.g. 'PRUSA_XL', 'PRUSA_MK4S', 'PRUSA_CORE_ONE'
    status              VARCHAR(30)     NOT NULL DEFAULT 'IDLE',        -- 'IDLE', 'PRINTING', 'MAINTENANCE', 'OFFLINE'
    current_material    VARCHAR(30),                                    -- Loaded material, e.g. 'PLA', 'PETG', 'ABS'
    current_colour      VARCHAR(30),                                    -- Loaded colour, e.g. 'Prusa Orange', 'Galaxy Black'
    created_at          TIMESTAMP       NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT now()
);

CREATE INDEX idx_printers_status ON printers (status);
CREATE INDEX idx_printers_model ON printers (model);

-- ============================================================================
-- 2. JOBS TABLE
-- Enforces print job lifecycle (QUEUED -> PRINTING -> COMPLETED / FAILED / CANCELLED).
-- ============================================================================
CREATE TABLE jobs (
    id                  BIGSERIAL       PRIMARY KEY,
    owner_uni_id        VARCHAR(20)     NOT NULL,
    printer_id          VARCHAR(50)     NOT NULL,
    file_name           VARCHAR(255),
    material            VARCHAR(30)     NOT NULL,
    estimated_grams     NUMERIC(10, 2)  NOT NULL DEFAULT 0.00,
    estimated_minutes   NUMERIC(10, 2)  NOT NULL DEFAULT 0.00,
    cost                NUMERIC(10, 2)  NOT NULL DEFAULT 0.00,
    status              VARCHAR(30)     NOT NULL DEFAULT 'QUEUED',      -- 'QUEUED', 'PRINTING', 'COMPLETED', 'FAILED', 'CANCELLED'
    queued_at           TIMESTAMP       NOT NULL DEFAULT now(),
    completed_at        TIMESTAMP,
    CONSTRAINT fk_jobs_owner_uni_id FOREIGN KEY (owner_uni_id) REFERENCES users (uni_id) ON DELETE RESTRICT,
    CONSTRAINT fk_jobs_printer_id FOREIGN KEY (printer_id) REFERENCES printers (id) ON DELETE RESTRICT
);

CREATE INDEX idx_jobs_owner_uni_id ON jobs (owner_uni_id);
CREATE INDEX idx_jobs_printer_id ON jobs (printer_id);
CREATE INDEX idx_jobs_status ON jobs (status);
CREATE INDEX idx_jobs_queued_at ON jobs (queued_at);

-- ============================================================================
-- 3. TRANSACTIONS TABLE
-- Permanent, immutable ledger tracking all debits, refunds, and balance adjustments.
-- ============================================================================
CREATE TABLE transactions (
    id                  BIGSERIAL       PRIMARY KEY,
    owner_uni_id        VARCHAR(20)     NOT NULL,
    job_id              BIGINT,
    type                VARCHAR(20)     NOT NULL,                       -- 'DEBIT', 'REFUND', 'TOPUP'
    amount              NUMERIC(10, 2)  NOT NULL,
    balance_after       NUMERIC(10, 2)  NOT NULL,
    occurred_at         TIMESTAMP       NOT NULL DEFAULT now(),
    description         TEXT,
    CONSTRAINT fk_transactions_owner_uni_id FOREIGN KEY (owner_uni_id) REFERENCES users (uni_id) ON DELETE RESTRICT,
    CONSTRAINT fk_transactions_job_id FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE SET NULL
);

CREATE INDEX idx_transactions_owner_uni_id ON transactions (owner_uni_id);
CREATE INDEX idx_transactions_job_id ON transactions (job_id);
CREATE INDEX idx_transactions_occurred_at ON transactions (occurred_at);

-- ============================================================================
-- 4. REFUND REQUESTS TABLE
-- Secondary approval workflow for cancellations occurring outside the auto-refund window.
-- ============================================================================
CREATE TABLE refund_requests (
    id                  BIGSERIAL       PRIMARY KEY,
    job_id              BIGINT          NOT NULL,
    owner_uni_id        VARCHAR(20)     NOT NULL,
    amount              NUMERIC(10, 2)  NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING_APPROVAL', -- 'PENDING_APPROVAL', 'APPROVED', 'REJECTED'
    requested_at        TIMESTAMP       NOT NULL DEFAULT now(),
    decided_at          TIMESTAMP,
    CONSTRAINT fk_refund_requests_job_id FOREIGN KEY (job_id) REFERENCES jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_refund_requests_owner_uni_id FOREIGN KEY (owner_uni_id) REFERENCES users (uni_id) ON DELETE RESTRICT
);

CREATE INDEX idx_refund_requests_job_id ON refund_requests (job_id);
CREATE INDEX idx_refund_requests_owner_uni_id ON refund_requests (owner_uni_id);
CREATE INDEX idx_refund_requests_status ON refund_requests (status);

-- ============================================================================
-- 5. SEED DATA: MOCK PRINTERS
-- Inserts 3 initial physical printers corresponding to supported Prusa profiles.
-- ============================================================================
INSERT INTO printers (id, name, model, status, current_material, current_colour)
VALUES
    ('PRUSA_XL_1', 'Prusa XL (Dual Tool)', 'PRUSA_XL', 'IDLE', 'PLA', 'Prusa Orange'),
    ('PRUSA_MK4S_1', 'Prusa MK4S #1', 'PRUSA_MK4S', 'IDLE', 'PETG', 'Galaxy Black'),
    ('PRUSA_CORE_ONE_1', 'Prusa Core One #1', 'PRUSA_CORE_ONE', 'IDLE', 'PLA', 'White');

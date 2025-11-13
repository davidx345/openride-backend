-- Update reconciliation_records table schema to match entity

-- Drop old reconciliation_status enum if exists
DROP TYPE IF EXISTS reconciliation_status CASCADE;

-- Create new reconciliation_status enum
CREATE TYPE reconciliation_status AS ENUM (
    'MATCHED',
    'DISCREPANCY',
    'FAILED'
);

-- Drop old reconciliation_records table
DROP TABLE IF EXISTS reconciliation_records CASCADE;

-- Recreate reconciliation_records with correct schema
CREATE TABLE reconciliation_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_date DATE NOT NULL UNIQUE,
    total_local_payments INTEGER NOT NULL DEFAULT 0,
    total_korapay_payments INTEGER NOT NULL DEFAULT 0,
    total_local_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    total_korapay_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    discrepancy_count INTEGER NOT NULL DEFAULT 0,
    status reconciliation_status NOT NULL,
    notes VARCHAR(1000),
    discrepancies TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_reconciliation_date ON reconciliation_records(reconciliation_date DESC);
CREATE INDEX idx_reconciliation_status ON reconciliation_records(status);

-- Comments
COMMENT ON TABLE reconciliation_records IS 'Daily reconciliation results comparing local payments with Korapay records';
COMMENT ON COLUMN reconciliation_records.discrepancies IS 'JSON array of discrepancy details';
COMMENT ON COLUMN reconciliation_records.status IS 'MATCHED=all matched, DISCREPANCY=found issues, FAILED=reconciliation failed';

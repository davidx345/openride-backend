-- Migration: Create disputes table for admin dispute resolution workflow
-- Phase 11: Admin Dashboard & Support APIs

CREATE TYPE dispute_status AS ENUM ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'REJECTED');
CREATE TYPE dispute_type AS ENUM ('PAYMENT', 'BOOKING', 'DRIVER_BEHAVIOR', 'RIDER_BEHAVIOR', 'OTHER');

CREATE TABLE disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL,
    reporter_id UUID NOT NULL,
    reported_id UUID,
    dispute_type dispute_type NOT NULL,
    status dispute_status NOT NULL DEFAULT 'OPEN',
    subject VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    evidence_urls TEXT[],
    resolution_notes TEXT,
    resolved_by UUID,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    CONSTRAINT fk_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT fk_reporter FOREIGN KEY (reporter_id) REFERENCES users(id),
    CONSTRAINT fk_reported FOREIGN KEY (reported_id) REFERENCES users(id),
    CONSTRAINT fk_resolved_by FOREIGN KEY (resolved_by) REFERENCES users(id)
);

-- Indexes for efficient queries
CREATE INDEX idx_disputes_booking ON disputes(booking_id);
CREATE INDEX idx_disputes_reporter ON disputes(reporter_id);
CREATE INDEX idx_disputes_status ON disputes(status);
CREATE INDEX idx_disputes_type ON disputes(dispute_type);
CREATE INDEX idx_disputes_created_at ON disputes(created_at DESC);

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_disputes_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_disputes_updated_at
    BEFORE UPDATE ON disputes
    FOR EACH ROW
    EXECUTE FUNCTION update_disputes_updated_at();

COMMENT ON TABLE disputes IS 'Stores booking disputes and support tickets for admin resolution';
COMMENT ON COLUMN disputes.evidence_urls IS 'Array of URLs to evidence (screenshots, photos, etc.)';
COMMENT ON COLUMN disputes.resolution_notes IS 'Admin notes about how the dispute was resolved';

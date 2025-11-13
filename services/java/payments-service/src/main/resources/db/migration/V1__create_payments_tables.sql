-- Phase 5: Payments Service Database Schema
-- Payment processing with Korapay integration

-- Create payment status enum
CREATE TYPE payment_status AS ENUM (
    'INITIATED',
    'PENDING', 
    'SUCCESS',
    'FAILED',
    'REFUNDED',
    'COMPLETED'
);

-- Create payment method enum
CREATE TYPE payment_method AS ENUM (
    'CARD',
    'BANK_TRANSFER',
    'USSD',
    'MOBILE_MONEY'
);

-- Main payments table
CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL,
    rider_id UUID NOT NULL,
    amount DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'NGN',
    status payment_status NOT NULL DEFAULT 'INITIATED',
    payment_method payment_method,
    
    -- Korapay specific fields
    korapay_reference VARCHAR(255) NOT NULL UNIQUE,
    korapay_transaction_id VARCHAR(255) UNIQUE,
    korapay_checkout_url TEXT,
    korapay_customer_email VARCHAR(255),
    korapay_customer_name VARCHAR(255),
    
    -- Metadata
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    failure_reason TEXT,
    refund_reason TEXT,
    refund_amount DECIMAL(10,2),
    refunded_at TIMESTAMP,
    
    -- Timestamps
    initiated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_payments_booking ON payments(booking_id);
CREATE INDEX idx_payments_rider ON payments(rider_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_status_created ON payments(status, created_at DESC);
CREATE INDEX idx_payments_korapay_ref ON payments(korapay_reference);
CREATE INDEX idx_payments_korapay_txn ON payments(korapay_transaction_id) WHERE korapay_transaction_id IS NOT NULL;
CREATE INDEX idx_payments_idempotency ON payments(idempotency_key);
CREATE INDEX idx_payments_expires_at ON payments(expires_at) WHERE status = 'PENDING';

-- Payment events audit trail
CREATE TABLE payment_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    previous_status payment_status,
    new_status payment_status NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_payment FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE CASCADE
);

CREATE INDEX idx_payment_events_payment ON payment_events(payment_id, created_at DESC);
CREATE INDEX idx_payment_events_type ON payment_events(event_type);

-- Reconciliation records
CREATE TYPE reconciliation_status AS ENUM ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED');

CREATE TABLE reconciliation_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_date DATE NOT NULL,
    total_payments_count INTEGER NOT NULL DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    korapay_count INTEGER NOT NULL DEFAULT 0,
    korapay_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    discrepancy_count INTEGER NOT NULL DEFAULT 0,
    discrepancies JSONB,
    status reconciliation_status NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    UNIQUE(reconciliation_date)
);

CREATE INDEX idx_reconciliation_date ON reconciliation_records(reconciliation_date DESC);
CREATE INDEX idx_reconciliation_status ON reconciliation_records(status);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers to auto-update updated_at
CREATE TRIGGER update_payments_updated_at BEFORE UPDATE ON payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_reconciliation_updated_at BEFORE UPDATE ON reconciliation_records
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE payments IS 'Main table for payment transactions processed through Korapay';
COMMENT ON COLUMN payments.korapay_reference IS 'Unique reference generated for Korapay transaction';
COMMENT ON COLUMN payments.idempotency_key IS 'Client-provided key to prevent duplicate payments';
COMMENT ON COLUMN payments.expires_at IS 'Time when pending payment expires (typically 15 minutes from initiation)';

COMMENT ON TABLE payment_events IS 'Audit trail of all payment status changes and events';
COMMENT ON TABLE reconciliation_records IS 'Daily reconciliation results comparing local payments with Korapay records';

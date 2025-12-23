-- ============================================================================
-- Migration V5: Add missing schema elements for all Java services
-- This migration fixes schema validation errors when services start up
-- ============================================================================

-- ============================================================================
-- 1. OTP Requests Table (auth-service)
-- ============================================================================
CREATE TABLE IF NOT EXISTS otp_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(20) NOT NULL,
    otp_code VARCHAR(10) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INTEGER NOT NULL DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_otp_requests_phone ON otp_requests(phone_number);
CREATE INDEX IF NOT EXISTS idx_otp_requests_expires ON otp_requests(expires_at);

-- ============================================================================
-- 2. Audit Logs Table (admin-service)
-- ============================================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor_id UUID,
    actor_type VARCHAR(50),
    actor_role VARCHAR(20),
    changes JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    request_id VARCHAR(100),
    service_name VARCHAR(100),
    endpoint VARCHAR(255),
    http_method VARCHAR(10),
    status_code INTEGER,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor ON audit_logs(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON audit_logs(action);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity_type ON audit_logs(entity_type);
CREATE INDEX IF NOT EXISTS idx_audit_logs_service ON audit_logs(service_name);
CREATE INDEX IF NOT EXISTS idx_audit_logs_request_id ON audit_logs(request_id);

-- ============================================================================
-- 3. Merkle Batches Table (ticketing-service - prerequisite for blockchain_anchors)
-- ============================================================================
CREATE TABLE IF NOT EXISTS merkle_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_number BIGINT NOT NULL UNIQUE,
    merkle_root VARCHAR(66) NOT NULL,
    ticket_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    finalized_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_merkle_batches_status ON merkle_batches(status);
CREATE INDEX IF NOT EXISTS idx_merkle_batches_created_at ON merkle_batches(created_at);

-- ============================================================================
-- 4. Blockchain Anchors Table (ticketing-service)
-- ============================================================================
CREATE TABLE IF NOT EXISTS blockchain_anchors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merkle_batch_id UUID NOT NULL,
    blockchain_type VARCHAR(50) NOT NULL,
    transaction_hash VARCHAR(66),
    block_number BIGINT,
    confirmation_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    gas_price BIGINT,
    gas_limit BIGINT,
    gas_used BIGINT,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    submitted_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_anchor_batch FOREIGN KEY (merkle_batch_id) 
        REFERENCES merkle_batches(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_blockchain_anchors_batch_id ON blockchain_anchors(merkle_batch_id);
CREATE INDEX IF NOT EXISTS idx_blockchain_anchors_status ON blockchain_anchors(status);
CREATE INDEX IF NOT EXISTS idx_blockchain_anchors_tx_hash ON blockchain_anchors(transaction_hash);
CREATE INDEX IF NOT EXISTS idx_blockchain_anchors_block_number ON blockchain_anchors(block_number);
CREATE INDEX IF NOT EXISTS idx_blockchain_anchors_submitted_at ON blockchain_anchors(submitted_at);

-- ============================================================================
-- 5. Add verified_at column to bank_accounts (payouts-service)
-- ============================================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'bank_accounts' AND column_name = 'verified_at'
    ) THEN
        ALTER TABLE bank_accounts ADD COLUMN verified_at TIMESTAMP;
    END IF;
END $$;

-- ============================================================================
-- 6. Add is_verified column to driver_profiles (user-service)
-- ============================================================================
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'driver_profiles' AND column_name = 'is_verified'
    ) THEN
        ALTER TABLE driver_profiles ADD COLUMN is_verified BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

-- ============================================================================
-- 7. Payment Events Table (payments-service)
-- ============================================================================
CREATE TABLE IF NOT EXISTS payment_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    previous_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_payment_events_payment ON payment_events(payment_id, created_at);
CREATE INDEX IF NOT EXISTS idx_payment_events_type ON payment_events(event_type);

-- ============================================================================
-- 8. Tickets Table (ticketing-service) - if missing
-- ============================================================================
CREATE TABLE IF NOT EXISTS tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL,
    ticket_number VARCHAR(50) NOT NULL UNIQUE,
    qr_code_data TEXT NOT NULL,
    signature TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    valid_from TIMESTAMP NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    merkle_batch_id UUID,
    merkle_leaf_index INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_tickets_booking ON tickets(booking_id);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(status);
CREATE INDEX IF NOT EXISTS idx_tickets_number ON tickets(ticket_number);

-- ============================================================================
-- 9. Ticket Verifications Table (ticketing-service) - if missing
-- ============================================================================
CREATE TABLE IF NOT EXISTS ticket_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    verifier_id UUID,
    verification_method VARCHAR(20) NOT NULL,
    verification_result VARCHAR(20) NOT NULL,
    device_info TEXT,
    location_lat DECIMAL(10, 8),
    location_lng DECIMAL(11, 8),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ticket_verifications_ticket ON ticket_verifications(ticket_id);

-- ============================================================================
-- 10. Signing Keys Table (ticketing-service) - if missing
-- ============================================================================
CREATE TABLE IF NOT EXISTS signing_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_id VARCHAR(50) NOT NULL UNIQUE,
    public_key TEXT NOT NULL,
    algorithm VARCHAR(20) NOT NULL DEFAULT 'ECDSA',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP,
    revoked_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_signing_keys_key_id ON signing_keys(key_id);
CREATE INDEX IF NOT EXISTS idx_signing_keys_status ON signing_keys(status);

-- ============================================================================
-- 11. User Suspensions Table (admin-service) - if missing
-- ============================================================================
CREATE TABLE IF NOT EXISTS user_suspensions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    reason TEXT NOT NULL,
    suspended_by UUID,
    suspended_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP,
    lifted_at TIMESTAMP,
    lifted_by UUID,
    lift_reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX IF NOT EXISTS idx_user_suspensions_user ON user_suspensions(user_id);
CREATE INDEX IF NOT EXISTS idx_user_suspensions_status ON user_suspensions(status);

-- ============================================================================
-- 12. Disputes Table (admin-service) - if missing
-- ============================================================================
CREATE TABLE IF NOT EXISTS disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID,
    complainant_id UUID NOT NULL,
    respondent_id UUID,
    dispute_type VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    assigned_to UUID,
    resolution TEXT,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_disputes_booking ON disputes(booking_id);
CREATE INDEX IF NOT EXISTS idx_disputes_status ON disputes(status);
CREATE INDEX IF NOT EXISTS idx_disputes_complainant ON disputes(complainant_id);

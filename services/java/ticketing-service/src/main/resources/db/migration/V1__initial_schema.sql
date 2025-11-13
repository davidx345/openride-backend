-- Ticketing Service Database Schema
-- Version: V1__initial_schema.sql

-- Create ENUM types for ticket and blockchain status
CREATE TYPE ticket_status AS ENUM ('PENDING', 'VALID', 'USED', 'EXPIRED', 'REVOKED');
CREATE TYPE verification_result AS ENUM ('VALID', 'INVALID', 'EXPIRED', 'REVOKED', 'NOT_FOUND');
CREATE TYPE verification_method AS ENUM ('SIGNATURE', 'MERKLE_PROOF', 'BLOCKCHAIN', 'DATABASE');
CREATE TYPE merkle_batch_status AS ENUM ('PENDING', 'BUILDING', 'READY', 'ANCHORED', 'FAILED');
CREATE TYPE blockchain_anchor_status AS ENUM ('PENDING', 'SUBMITTED', 'CONFIRMED', 'FAILED', 'EXPIRED');
CREATE TYPE blockchain_type AS ENUM ('POLYGON', 'HYPERLEDGER', 'ETHEREUM', 'OTHER');

-- Tickets table: Core ticket information with cryptographic data
CREATE TABLE tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL UNIQUE,
    rider_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    route_id UUID NOT NULL,
    trip_date TIMESTAMP NOT NULL,
    seat_number INTEGER NOT NULL,
    pickup_stop VARCHAR(255) NOT NULL,
    dropoff_stop VARCHAR(255) NOT NULL,
    fare DECIMAL(10, 2) NOT NULL,
    
    -- Cryptographic fields
    canonical_json TEXT NOT NULL,
    hash VARCHAR(64) NOT NULL UNIQUE,
    signature TEXT NOT NULL,
    qr_code TEXT, -- Base64 encoded PNG
    
    -- Status and batch information
    status ticket_status NOT NULL DEFAULT 'PENDING',
    merkle_batch_id UUID,
    
    -- Validity period
    expires_at TIMESTAMP NOT NULL,
    
    -- Audit fields
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- Constraints
    CONSTRAINT chk_seat_positive CHECK (seat_number > 0),
    CONSTRAINT chk_fare_positive CHECK (fare > 0),
    CONSTRAINT chk_trip_future CHECK (trip_date >= created_at)
);

-- Indexes for tickets table
CREATE INDEX idx_tickets_booking_id ON tickets(booking_id);
CREATE INDEX idx_tickets_rider_id ON tickets(rider_id);
CREATE INDEX idx_tickets_driver_id ON tickets(driver_id);
CREATE INDEX idx_tickets_route_id ON tickets(route_id);
CREATE INDEX idx_tickets_status ON tickets(status);
CREATE INDEX idx_tickets_trip_date ON tickets(trip_date);
CREATE INDEX idx_tickets_hash ON tickets(hash);
CREATE INDEX idx_tickets_merkle_batch_id ON tickets(merkle_batch_id);
CREATE INDEX idx_tickets_expires_at ON tickets(expires_at);
CREATE INDEX idx_tickets_created_at ON tickets(created_at DESC);

-- Composite indexes for common queries
CREATE INDEX idx_tickets_rider_status ON tickets(rider_id, status);
CREATE INDEX idx_tickets_driver_trip_date ON tickets(driver_id, trip_date);

-- Ticket verification logs: Audit trail for all verification attempts
CREATE TABLE ticket_verification_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL,
    verifier_id UUID, -- Driver ID or admin ID
    verification_method verification_method NOT NULL,
    result verification_result NOT NULL,
    ip_address VARCHAR(45), -- Support IPv6
    user_agent VARCHAR(500),
    error_message TEXT,
    verification_time TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- Foreign key
    CONSTRAINT fk_verification_ticket FOREIGN KEY (ticket_id) 
        REFERENCES tickets(id) ON DELETE CASCADE
);

-- Indexes for verification logs
CREATE INDEX idx_verification_logs_ticket_id ON ticket_verification_logs(ticket_id);
CREATE INDEX idx_verification_logs_verifier_id ON ticket_verification_logs(verifier_id);
CREATE INDEX idx_verification_logs_result ON ticket_verification_logs(result);
CREATE INDEX idx_verification_logs_time ON ticket_verification_logs(verification_time DESC);
CREATE INDEX idx_verification_logs_method ON ticket_verification_logs(verification_method);

-- Merkle batches: Groups of tickets for blockchain anchoring
CREATE TABLE merkle_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merkle_root VARCHAR(64) NOT NULL,
    ticket_count INTEGER NOT NULL,
    blockchain_anchor_id UUID,
    status merkle_batch_status NOT NULL DEFAULT 'PENDING',
    
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    anchored_at TIMESTAMP,
    
    -- Constraints
    CONSTRAINT chk_ticket_count_positive CHECK (ticket_count > 0)
);

-- Indexes for merkle batches
CREATE INDEX idx_merkle_batches_status ON merkle_batches(status);
CREATE INDEX idx_merkle_batches_created_at ON merkle_batches(created_at DESC);
CREATE INDEX idx_merkle_batches_merkle_root ON merkle_batches(merkle_root);

-- Merkle proofs: Individual ticket proofs for verification
CREATE TABLE merkle_proofs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL UNIQUE,
    merkle_batch_id UUID NOT NULL,
    leaf_index INTEGER NOT NULL,
    proof_path TEXT NOT NULL, -- JSON array of hashes
    
    -- Metadata
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- Foreign keys
    CONSTRAINT fk_proof_ticket FOREIGN KEY (ticket_id) 
        REFERENCES tickets(id) ON DELETE CASCADE,
    CONSTRAINT fk_proof_batch FOREIGN KEY (merkle_batch_id) 
        REFERENCES merkle_batches(id) ON DELETE CASCADE,
    
    -- Constraints
    CONSTRAINT chk_leaf_index_non_negative CHECK (leaf_index >= 0)
);

-- Indexes for merkle proofs
CREATE INDEX idx_merkle_proofs_ticket_id ON merkle_proofs(ticket_id);
CREATE INDEX idx_merkle_proofs_batch_id ON merkle_proofs(merkle_batch_id);

-- Blockchain anchors: On-chain transaction records
CREATE TABLE blockchain_anchors (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merkle_batch_id UUID NOT NULL,
    blockchain_type blockchain_type NOT NULL,
    
    -- Transaction details
    transaction_hash VARCHAR(66), -- 0x prefix + 64 hex chars
    block_number BIGINT,
    confirmation_count INTEGER NOT NULL DEFAULT 0,
    status blockchain_anchor_status NOT NULL DEFAULT 'PENDING',
    
    -- Gas and cost tracking
    gas_price BIGINT, -- Wei for Ethereum/Polygon
    gas_limit BIGINT,
    gas_used BIGINT,
    
    -- Error handling
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    
    -- Timestamps
    submitted_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- Foreign key
    CONSTRAINT fk_anchor_batch FOREIGN KEY (merkle_batch_id) 
        REFERENCES merkle_batches(id) ON DELETE CASCADE,
    
    -- Constraints
    CONSTRAINT chk_confirmation_non_negative CHECK (confirmation_count >= 0),
    CONSTRAINT chk_retry_non_negative CHECK (retry_count >= 0)
);

-- Indexes for blockchain anchors
CREATE INDEX idx_blockchain_anchors_batch_id ON blockchain_anchors(merkle_batch_id);
CREATE INDEX idx_blockchain_anchors_status ON blockchain_anchors(status);
CREATE INDEX idx_blockchain_anchors_tx_hash ON blockchain_anchors(transaction_hash);
CREATE INDEX idx_blockchain_anchors_block_number ON blockchain_anchors(block_number);
CREATE INDEX idx_blockchain_anchors_submitted_at ON blockchain_anchors(submitted_at DESC);

-- Update foreign key in tickets table for merkle_batch_id
ALTER TABLE tickets 
    ADD CONSTRAINT fk_tickets_merkle_batch 
    FOREIGN KEY (merkle_batch_id) REFERENCES merkle_batches(id) ON DELETE SET NULL;

-- Update foreign key in merkle_batches for blockchain_anchor_id
ALTER TABLE merkle_batches 
    ADD CONSTRAINT fk_batch_anchor 
    FOREIGN KEY (blockchain_anchor_id) REFERENCES blockchain_anchors(id) ON DELETE SET NULL;

-- Function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger for tickets table
CREATE TRIGGER update_tickets_updated_at 
    BEFORE UPDATE ON tickets
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments for documentation
COMMENT ON TABLE tickets IS 'Core tickets table with cryptographic signatures and QR codes';
COMMENT ON TABLE ticket_verification_logs IS 'Audit trail for all ticket verification attempts';
COMMENT ON TABLE merkle_batches IS 'Batches of tickets for efficient blockchain anchoring';
COMMENT ON TABLE merkle_proofs IS 'Merkle tree proofs for individual ticket verification';
COMMENT ON TABLE blockchain_anchors IS 'On-chain transaction records for ticket batches';

COMMENT ON COLUMN tickets.canonical_json IS 'Canonical JSON representation used for hashing';
COMMENT ON COLUMN tickets.hash IS 'SHA-256 hash of canonical JSON';
COMMENT ON COLUMN tickets.signature IS 'ECDSA signature of the hash';
COMMENT ON COLUMN tickets.qr_code IS 'Base64 encoded PNG QR code';

COMMENT ON COLUMN merkle_batches.merkle_root IS 'Root hash of the Merkle tree';
COMMENT ON COLUMN merkle_proofs.proof_path IS 'JSON array of sibling hashes for Merkle proof verification';

COMMENT ON COLUMN blockchain_anchors.transaction_hash IS 'Blockchain transaction hash (0x-prefixed)';
COMMENT ON COLUMN blockchain_anchors.gas_price IS 'Gas price in Wei (for Ethereum/Polygon)';

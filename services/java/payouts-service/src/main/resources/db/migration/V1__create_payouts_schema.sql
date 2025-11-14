-- V1__create_payouts_schema.sql

-- Create ENUM types
CREATE TYPE payout_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'PROCESSING', 'COMPLETED', 'FAILED');
CREATE TYPE settlement_status AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED');
CREATE TYPE ledger_entry_type AS ENUM ('CREDIT', 'DEBIT');
CREATE TYPE transaction_type AS ENUM ('EARNING', 'PAYOUT', 'REFUND', 'ADJUSTMENT');

-- Driver Wallets Table
CREATE TABLE driver_wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL UNIQUE,
    available_balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    pending_payout DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    total_earnings DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    total_paid_out DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    last_payout_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT wallet_balance_non_negative CHECK (available_balance >= 0),
    CONSTRAINT pending_payout_non_negative CHECK (pending_payout >= 0),
    CONSTRAINT total_earnings_non_negative CHECK (total_earnings >= 0)
);

-- Earnings Ledger Table (Double-Entry Accounting)
CREATE TABLE earnings_ledger (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL,
    entry_type ledger_entry_type NOT NULL,
    transaction_type transaction_type NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    balance_after DECIMAL(15, 2) NOT NULL,
    reference_id UUID,
    reference_type VARCHAR(50),
    description TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ledger_amount_positive CHECK (amount > 0)
);

-- Bank Accounts Table
CREATE TABLE bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL,
    account_number VARCHAR(10) NOT NULL,
    account_name VARCHAR(255) NOT NULL,
    bank_code VARCHAR(10) NOT NULL,
    bank_name VARCHAR(255) NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_driver_account UNIQUE (driver_id, account_number, bank_code)
);

-- Payout Requests Table
CREATE TABLE payout_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id UUID NOT NULL,
    bank_account_id UUID NOT NULL REFERENCES bank_accounts(id),
    amount DECIMAL(15, 2) NOT NULL,
    status payout_status NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMP NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMP,
    reviewed_by UUID,
    reviewer_notes TEXT,
    settlement_id UUID,
    completed_at TIMESTAMP,
    failure_reason TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT payout_amount_positive CHECK (amount > 0)
);

-- Settlements Table (Batch Processing)
CREATE TABLE settlements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_reference VARCHAR(100) NOT NULL UNIQUE,
    total_amount DECIMAL(15, 2) NOT NULL,
    payout_count INTEGER NOT NULL,
    status settlement_status NOT NULL DEFAULT 'PENDING',
    initiated_by UUID NOT NULL,
    initiated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    failure_reason TEXT,
    provider VARCHAR(50),
    provider_reference VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Audit Logs Table
CREATE TABLE payout_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    performed_by UUID,
    old_values JSONB,
    new_values JSONB,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_wallet_driver ON driver_wallets(driver_id);
CREATE INDEX idx_ledger_driver ON earnings_ledger(driver_id, created_at DESC);
CREATE INDEX idx_ledger_reference ON earnings_ledger(reference_id, reference_type);
CREATE INDEX idx_bank_accounts_driver ON bank_accounts(driver_id);
CREATE INDEX idx_bank_accounts_primary ON bank_accounts(driver_id, is_primary) WHERE is_primary = TRUE;
CREATE INDEX idx_payout_requests_driver ON payout_requests(driver_id);
CREATE INDEX idx_payout_requests_status ON payout_requests(status, requested_at DESC);
CREATE INDEX idx_payout_requests_settlement ON payout_requests(settlement_id);
CREATE INDEX idx_settlements_status ON settlements(status, initiated_at DESC);
CREATE INDEX idx_settlements_batch_ref ON settlements(batch_reference);
CREATE INDEX idx_audit_logs_entity ON payout_audit_logs(entity_type, entity_id, created_at DESC);

-- Function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers for updated_at
CREATE TRIGGER update_driver_wallets_updated_at BEFORE UPDATE ON driver_wallets
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bank_accounts_updated_at BEFORE UPDATE ON bank_accounts
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_payout_requests_updated_at BEFORE UPDATE ON payout_requests
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_settlements_updated_at BEFORE UPDATE ON settlements
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Function to ensure only one primary bank account per driver
CREATE OR REPLACE FUNCTION ensure_single_primary_bank_account()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.is_primary = TRUE THEN
        UPDATE bank_accounts
        SET is_primary = FALSE
        WHERE driver_id = NEW.driver_id
        AND id != NEW.id
        AND is_primary = TRUE;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ensure_primary_bank_account BEFORE INSERT OR UPDATE ON bank_accounts
    FOR EACH ROW WHEN (NEW.is_primary = TRUE)
    EXECUTE FUNCTION ensure_single_primary_bank_account();

-- Comments for documentation
COMMENT ON TABLE driver_wallets IS 'Stores driver wallet balances and payout tracking';
COMMENT ON TABLE earnings_ledger IS 'Double-entry ledger for all financial transactions';
COMMENT ON TABLE bank_accounts IS 'Driver bank account information for payouts';
COMMENT ON TABLE payout_requests IS 'Driver payout withdrawal requests';
COMMENT ON TABLE settlements IS 'Batch settlement processing records';
COMMENT ON TABLE payout_audit_logs IS 'Audit trail for all payout-related actions';

COMMENT ON COLUMN driver_wallets.available_balance IS 'Current available balance for payout';
COMMENT ON COLUMN driver_wallets.pending_payout IS 'Amount in pending payout requests';
COMMENT ON COLUMN earnings_ledger.entry_type IS 'CREDIT increases balance, DEBIT decreases';
COMMENT ON COLUMN bank_accounts.is_verified IS 'Verified via Paystack/Flutterwave API';
COMMENT ON COLUMN payout_requests.status IS 'PENDING -> APPROVED/REJECTED -> PROCESSING -> COMPLETED/FAILED';

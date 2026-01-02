-- Migration: Add verification tables for Passenger and Captain verification
-- This supports the multi-step verification process required for all users

-- =====================================================
-- STEP 1: Update existing enums
-- =====================================================

-- Drop and recreate user_role enum with new values
ALTER TYPE user_role RENAME TO user_role_old;
CREATE TYPE user_role AS ENUM ('PASSENGER', 'CAPTAIN', 'ADMIN');

-- Update existing data: RIDER -> PASSENGER, DRIVER -> CAPTAIN
UPDATE users SET role = 'PASSENGER' WHERE role::text = 'RIDER';
UPDATE users SET role = 'CAPTAIN' WHERE role::text = 'DRIVER';

-- Alter column to use new enum
ALTER TABLE users 
    ALTER COLUMN role TYPE user_role USING role::text::user_role;

DROP TYPE user_role_old;

-- Drop and recreate kyc_status enum with new values
ALTER TYPE kyc_status RENAME TO kyc_status_old;
CREATE TYPE kyc_status AS ENUM (
    'UNVERIFIED',
    'IDENTITY_PENDING',
    'IDENTITY_VERIFIED',
    'AFFILIATION_PENDING',
    'FULLY_VERIFIED',
    'CAPTAIN_PENDING',
    'CAPTAIN_VERIFIED',
    'REJECTED',
    'SUSPENDED'
);

-- Update existing data
UPDATE users SET kyc_status = 'UNVERIFIED' WHERE kyc_status::text = 'NONE';
UPDATE users SET kyc_status = 'CAPTAIN_PENDING' WHERE kyc_status::text = 'PENDING';
UPDATE users SET kyc_status = 'CAPTAIN_VERIFIED' WHERE kyc_status::text = 'VERIFIED';
UPDATE users SET kyc_status = 'REJECTED' WHERE kyc_status::text = 'REJECTED';

-- Alter column to use new enum
ALTER TABLE users 
    ALTER COLUMN kyc_status TYPE kyc_status USING kyc_status::text::kyc_status;

DROP TYPE kyc_status_old;

-- =====================================================
-- STEP 2: Create new enum types
-- =====================================================

-- Government ID types
CREATE TYPE government_id_type AS ENUM (
    'NATIONAL_ID',
    'NIN_SLIP',
    'INTERNATIONAL_PASSPORT',
    'VOTERS_CARD',
    'DRIVERS_LICENSE'
);

-- Affiliation types
CREATE TYPE affiliation_type AS ENUM (
    'STUDENT',
    'EMPLOYEE'
);

-- Verification methods
CREATE TYPE verification_method AS ENUM (
    'ID_CARD',
    'EMAIL'
);

-- =====================================================
-- STEP 3: Create identity_verifications table
-- =====================================================
CREATE TABLE IF NOT EXISTS identity_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    nin_encrypted TEXT,
    nin_verified BOOLEAN NOT NULL DEFAULT FALSE,
    government_id_type government_id_type,
    government_id_url TEXT,
    selfie_url TEXT,
    selfie_match_score DECIMAL(3, 2),
    dojah_reference_id VARCHAR(100),
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    rejection_reason TEXT,
    verified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_identity_user_id ON identity_verifications(user_id);
CREATE INDEX idx_identity_verified ON identity_verifications(is_verified);

CREATE TRIGGER update_identity_verifications_updated_at
    BEFORE UPDATE ON identity_verifications
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- STEP 4: Create affiliation_verifications table
-- =====================================================
CREATE TABLE IF NOT EXISTS affiliation_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    affiliation_type affiliation_type NOT NULL,
    organization_name VARCHAR(200),
    verification_method verification_method NOT NULL,
    id_card_url TEXT,
    verification_email VARCHAR(255),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verification_token VARCHAR(255),
    token_expires_at TIMESTAMP,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    rejection_reason TEXT,
    verified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_affiliation_user_id ON affiliation_verifications(user_id);
CREATE INDEX idx_affiliation_verified ON affiliation_verifications(is_verified);
CREATE INDEX idx_affiliation_token ON affiliation_verifications(email_verification_token);

CREATE TRIGGER update_affiliation_verifications_updated_at
    BEFORE UPDATE ON affiliation_verifications
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- STEP 5: Create captain_verifications table
-- =====================================================
CREATE TABLE IF NOT EXISTS captain_verifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    license_number_encrypted TEXT,
    license_photo_url TEXT,
    license_verified BOOLEAN NOT NULL DEFAULT FALSE,
    license_expiry_date DATE,
    dojah_reference_id VARCHAR(100),
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    rejection_reason TEXT,
    verified_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_captain_user_id ON captain_verifications(user_id);
CREATE INDEX idx_captain_verified ON captain_verifications(is_verified);

CREATE TRIGGER update_captain_verifications_updated_at
    BEFORE UPDATE ON captain_verifications
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- STEP 6: Create vehicles table
-- =====================================================
CREATE TABLE IF NOT EXISTS vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    make VARCHAR(50) NOT NULL,
    model VARCHAR(50) NOT NULL,
    year INT NOT NULL,
    color VARCHAR(30) NOT NULL,
    license_plate VARCHAR(20) NOT NULL,
    seats_available INT NOT NULL DEFAULT 4,
    registration_url TEXT,
    insurance_url TEXT,
    photo_front_url TEXT,
    photo_back_url TEXT,
    photo_interior_url TEXT,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_vehicles_user_id ON vehicles(user_id);
CREATE INDEX idx_vehicles_active ON vehicles(is_active);
CREATE INDEX idx_vehicles_license_plate ON vehicles(license_plate);

CREATE TRIGGER update_vehicles_updated_at
    BEFORE UPDATE ON vehicles
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- STEP 7: Add selfie_url to users table
-- =====================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS selfie_url TEXT;

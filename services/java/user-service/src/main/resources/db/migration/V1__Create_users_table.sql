-- Create user role enum
CREATE TYPE user_role AS ENUM ('RIDER', 'DRIVER', 'ADMIN');

-- Create KYC status enum
CREATE TYPE kyc_status AS ENUM ('NONE', 'PENDING', 'VERIFIED', 'REJECTED');

-- Create users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone VARCHAR(20) NOT NULL UNIQUE,
    full_name VARCHAR(255),
    email VARCHAR(255),
    role user_role NOT NULL DEFAULT 'RIDER',
    kyc_status kyc_status NOT NULL DEFAULT 'NONE',
    rating DECIMAL(2, 1) DEFAULT 0.0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Create indexes
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_kyc_status ON users(kyc_status);
CREATE INDEX idx_users_active ON users(is_active);

-- Create trigger to update updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

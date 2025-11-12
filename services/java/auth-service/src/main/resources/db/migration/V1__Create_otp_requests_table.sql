-- Create otp_requests table for storing OTP verification data
CREATE TABLE IF NOT EXISTS otp_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phone_number VARCHAR(20) NOT NULL,
    otp_code VARCHAR(10) NOT NULL,
    verified BOOLEAN DEFAULT FALSE,
    attempts INT DEFAULT 0,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Create index for efficient lookup by phone number and expiration
CREATE INDEX idx_otp_phone_expires ON otp_requests(phone_number, expires_at);

-- Create index for cleanup queries
CREATE INDEX idx_otp_expires ON otp_requests(expires_at);

-- Create driver_profiles table
CREATE TABLE IF NOT EXISTS driver_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    bvn_encrypted TEXT,
    license_number_encrypted TEXT,
    license_photo_url TEXT,
    vehicle_photo_url TEXT,
    kyc_notes TEXT,
    total_trips INT DEFAULT 0,
    total_earnings DECIMAL(12, 2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX idx_driver_user_id ON driver_profiles(user_id);

-- Create trigger to update updated_at
CREATE TRIGGER update_driver_profiles_updated_at
    BEFORE UPDATE ON driver_profiles
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Add cancellation_rate column to driver_profiles table
ALTER TABLE driver_profiles 
ADD COLUMN IF NOT EXISTS cancellation_rate DECIMAL(5, 2) DEFAULT 0.00;

-- Add index for cancellation_rate
CREATE INDEX IF NOT EXISTS idx_driver_cancellation_rate ON driver_profiles(cancellation_rate);

-- Add comment
COMMENT ON COLUMN driver_profiles.cancellation_rate IS 'Percentage of cancelled trips (0.00 to 100.00)';

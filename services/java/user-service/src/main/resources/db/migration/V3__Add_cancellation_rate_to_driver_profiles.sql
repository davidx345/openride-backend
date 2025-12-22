-- Add cancellation_rate and cancelled_trips columns to driver_profiles table
ALTER TABLE driver_profiles 
ADD COLUMN IF NOT EXISTS cancellation_rate DECIMAL(5, 2) DEFAULT 0.00,
ADD COLUMN IF NOT EXISTS cancelled_trips INTEGER DEFAULT 0;

-- Add indexes
CREATE INDEX IF NOT EXISTS idx_driver_cancellation_rate ON driver_profiles(cancellation_rate);
CREATE INDEX IF NOT EXISTS idx_driver_cancelled_trips ON driver_profiles(cancelled_trips);

-- Add comments
COMMENT ON COLUMN driver_profiles.cancellation_rate IS 'Percentage of cancelled trips (0.00 to 100.00)';
COMMENT ON COLUMN driver_profiles.cancelled_trips IS 'Total number of cancelled trips';

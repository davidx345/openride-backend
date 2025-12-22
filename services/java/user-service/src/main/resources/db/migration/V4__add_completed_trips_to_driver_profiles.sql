-- Add completed_trips column to driver_profiles table
ALTER TABLE driver_profiles 
ADD COLUMN IF NOT EXISTS completed_trips INTEGER DEFAULT 0;

-- Add index
CREATE INDEX IF NOT EXISTS idx_driver_completed_trips ON driver_profiles(completed_trips);

-- Add comment
COMMENT ON COLUMN driver_profiles.completed_trips IS 'Total number of completed trips';

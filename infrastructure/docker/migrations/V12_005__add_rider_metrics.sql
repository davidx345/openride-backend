-- Migration: V12_005__add_rider_metrics.sql
-- Description: Create rider_profiles table for rider metrics and behavior tracking
-- Author: OpenRide Platform Team
-- Date: 2025-11-14
-- Phase: 1.5 - Add Rider Metrics

-- Create rider_profiles table
CREATE TABLE rider_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE,
    completed_trips INTEGER DEFAULT 0 NOT NULL,
    cancelled_trips INTEGER DEFAULT 0 NOT NULL,
    no_show_count INTEGER DEFAULT 0 NOT NULL,
    total_spent NUMERIC(12, 2) DEFAULT 0 NOT NULL,
    average_rating NUMERIC(3, 2),
    rating_count INTEGER DEFAULT 0 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT fk_rider_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_completed_trips_non_negative CHECK (completed_trips >= 0),
    CONSTRAINT ck_cancelled_trips_non_negative CHECK (cancelled_trips >= 0),
    CONSTRAINT ck_no_show_count_non_negative CHECK (no_show_count >= 0),
    CONSTRAINT ck_total_spent_non_negative CHECK (total_spent >= 0),
    CONSTRAINT ck_rating_count_non_negative CHECK (rating_count >= 0),
    CONSTRAINT ck_average_rating_valid CHECK (average_rating IS NULL OR (average_rating >= 0 AND average_rating <= 5))
);

-- Create indexes for filtering and sorting
CREATE INDEX idx_rider_profiles_user_id ON rider_profiles(user_id);
CREATE INDEX idx_rider_profiles_average_rating ON rider_profiles(average_rating);
CREATE INDEX idx_rider_profiles_completed_trips ON rider_profiles(completed_trips);
CREATE INDEX idx_rider_profiles_no_show ON rider_profiles(no_show_count);

-- Trigger function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_rider_profiles_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-update updated_at on change
CREATE TRIGGER trg_rider_profiles_updated_at
    BEFORE UPDATE ON rider_profiles
    FOR EACH ROW
    EXECUTE FUNCTION update_rider_profiles_updated_at();

-- Table and column comments
COMMENT ON TABLE rider_profiles IS 'Rider metrics and behavior tracking for fraud detection and personalization';
COMMENT ON COLUMN rider_profiles.id IS 'Unique rider profile identifier';
COMMENT ON COLUMN rider_profiles.user_id IS 'Reference to user (rider)';
COMMENT ON COLUMN rider_profiles.completed_trips IS 'Total number of successfully completed trips as rider';
COMMENT ON COLUMN rider_profiles.cancelled_trips IS 'Total number of trips cancelled by rider';
COMMENT ON COLUMN rider_profiles.no_show_count IS 'Number of times rider did not show up for booked trip';
COMMENT ON COLUMN rider_profiles.total_spent IS 'Total amount spent on trips';
COMMENT ON COLUMN rider_profiles.average_rating IS 'Average rating given by drivers (0-5 scale)';
COMMENT ON COLUMN rider_profiles.rating_count IS 'Total number of ratings received from drivers';

-- Data migration: Create rider profiles for existing users with RIDER role
INSERT INTO rider_profiles (user_id)
SELECT id
FROM users
WHERE role = 'RIDER'
  AND NOT EXISTS (SELECT 1 FROM rider_profiles WHERE user_id = users.id);

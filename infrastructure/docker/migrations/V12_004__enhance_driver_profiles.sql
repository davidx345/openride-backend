-- Migration: V12_004__enhance_driver_profiles.sql
-- Description: Add rating metrics and trip statistics to driver_profiles table
-- Author: OpenRide Platform Team
-- Date: 2025-11-14
-- Phase: 1.4 - Enhance Driver Profiles

-- Add new metric columns to driver_profiles table
ALTER TABLE driver_profiles
    ADD COLUMN rating_avg NUMERIC(3, 2),
    ADD COLUMN rating_count INTEGER DEFAULT 0 NOT NULL,
    ADD COLUMN cancellation_rate NUMERIC(5, 2),
    ADD COLUMN completed_trips INTEGER DEFAULT 0 NOT NULL,
    ADD COLUMN cancelled_trips INTEGER DEFAULT 0 NOT NULL,
    ADD COLUMN is_verified BOOLEAN DEFAULT false NOT NULL;

-- Create indexes for filtering and sorting
CREATE INDEX idx_driver_profiles_rating_avg ON driver_profiles(rating_avg);
CREATE INDEX idx_driver_profiles_is_verified ON driver_profiles(is_verified);
CREATE INDEX idx_driver_profiles_verified_rating ON driver_profiles(is_verified, rating_avg);

-- Add constraints for valid ratings
ALTER TABLE driver_profiles
    ADD CONSTRAINT ck_rating_avg_valid 
    CHECK (rating_avg IS NULL OR (rating_avg >= 0 AND rating_avg <= 5));

ALTER TABLE driver_profiles
    ADD CONSTRAINT ck_rating_count_non_negative 
    CHECK (rating_count >= 0);

ALTER TABLE driver_profiles
    ADD CONSTRAINT ck_cancellation_rate_valid 
    CHECK (cancellation_rate IS NULL OR (cancellation_rate >= 0 AND cancellation_rate <= 100));

ALTER TABLE driver_profiles
    ADD CONSTRAINT ck_completed_trips_non_negative 
    CHECK (completed_trips >= 0);

ALTER TABLE driver_profiles
    ADD CONSTRAINT ck_cancelled_trips_non_negative 
    CHECK (cancelled_trips >= 0);

-- Column comments
COMMENT ON COLUMN driver_profiles.rating_avg IS 'Average driver rating (0-5 scale)';
COMMENT ON COLUMN driver_profiles.rating_count IS 'Total number of ratings received';
COMMENT ON COLUMN driver_profiles.cancellation_rate IS 'Percentage of trips cancelled by driver (0-100)';
COMMENT ON COLUMN driver_profiles.completed_trips IS 'Total number of successfully completed trips';
COMMENT ON COLUMN driver_profiles.cancelled_trips IS 'Total number of trips cancelled by driver';
COMMENT ON COLUMN driver_profiles.is_verified IS 'Whether driver has been verified by admin';

-- Trigger function to auto-update cancellation_rate
CREATE OR REPLACE FUNCTION update_driver_cancellation_rate()
RETURNS TRIGGER AS $$
BEGIN
    IF (NEW.completed_trips + NEW.cancelled_trips) > 0 THEN
        NEW.cancellation_rate = ROUND(
            (NEW.cancelled_trips::NUMERIC / (NEW.completed_trips + NEW.cancelled_trips)) * 100,
            2
        );
    ELSE
        NEW.cancellation_rate = 0;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-update cancellation_rate on trip count changes
CREATE TRIGGER trg_driver_cancellation_rate
    BEFORE UPDATE OF completed_trips, cancelled_trips ON driver_profiles
    FOR EACH ROW
    EXECUTE FUNCTION update_driver_cancellation_rate();

-- Data migration: Initialize completed_trips from existing total_trips
UPDATE driver_profiles
SET completed_trips = COALESCE(total_trips, 0)
WHERE completed_trips = 0;

-- Data migration: Initialize cancellation_rate to 0 for existing drivers
UPDATE driver_profiles
SET cancellation_rate = 0
WHERE cancellation_rate IS NULL;

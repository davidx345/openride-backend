-- Migration: V12_006__add_search_id_to_bookings.sql
-- Description: Add search event linkage to bookings table for conversion tracking
-- Author: OpenRide Platform Team
-- Date: 2025-11-14
-- Phase: 1.6 - Link Bookings to Search Events

-- Add search event tracking columns to bookings table
ALTER TABLE bookings
    ADD COLUMN search_id UUID,
    ADD COLUMN candidate_rank INTEGER,
    ADD COLUMN candidate_count INTEGER;

-- Create index for search event analytics
CREATE INDEX idx_bookings_search_id ON bookings(search_id);
CREATE INDEX idx_bookings_search_rank ON bookings(search_id, candidate_rank);

-- Add constraint to ensure rank is positive when set
ALTER TABLE bookings
    ADD CONSTRAINT ck_candidate_rank_positive
    CHECK (candidate_rank IS NULL OR candidate_rank > 0);

-- Add constraint to ensure count is positive when set
ALTER TABLE bookings
    ADD CONSTRAINT ck_candidate_count_positive
    CHECK (candidate_count IS NULL OR candidate_count > 0);

-- Add constraint to ensure rank doesn't exceed count
ALTER TABLE bookings
    ADD CONSTRAINT ck_rank_within_count
    CHECK (
        candidate_rank IS NULL OR 
        candidate_count IS NULL OR 
        candidate_rank <= candidate_count
    );

-- Column comments
COMMENT ON COLUMN bookings.search_id IS 'Reference to search event that led to this booking (for conversion tracking)';
COMMENT ON COLUMN bookings.candidate_rank IS 'Rank of booked route in search results (1 = top result)';
COMMENT ON COLUMN bookings.candidate_count IS 'Total number of candidates returned in search results';

-- Phase 2.2: Driver Stats Aggregation
-- Create materialized view for precomputed driver statistics
-- This improves matching performance by avoiding real-time aggregations

-- Drop existing materialized view if it exists
DROP MATERIALIZED VIEW IF EXISTS driver_stats_agg CASCADE;

-- Create materialized view
CREATE MATERIALIZED VIEW driver_stats_agg AS
SELECT
    dp.driver_id,
    dp.rating_avg,
    dp.rating_count,
    dp.cancellation_rate,
    dp.completed_trips,
    dp.is_verified,
    dp.updated_at,
    -- Additional computed fields
    CASE
        WHEN dp.completed_trips >= 100 AND dp.rating_avg >= 4.5 AND dp.cancellation_rate < 0.05 THEN 'premium'
        WHEN dp.completed_trips >= 50 AND dp.rating_avg >= 4.0 AND dp.cancellation_rate < 0.10 THEN 'verified'
        WHEN dp.completed_trips >= 10 AND dp.rating_avg >= 3.5 THEN 'standard'
        ELSE 'new'
    END AS driver_tier,
    -- Active routes count
    (
        SELECT COUNT(*)
        FROM routes r
        WHERE r.driver_id = dp.driver_id
        AND r.status = 'active'
        AND r.seats_available > 0
    ) AS active_routes_count,
    -- Total available seats across all routes
    (
        SELECT COALESCE(SUM(r.seats_available), 0)
        FROM routes r
        WHERE r.driver_id = dp.driver_id
        AND r.status = 'active'
    ) AS total_available_seats
FROM
    driver_profiles dp
WHERE
    dp.rating_count > 0  -- Only include drivers with ratings
ORDER BY
    dp.rating_avg DESC,
    dp.completed_trips DESC;

-- Create index on driver_id for fast lookups
CREATE UNIQUE INDEX idx_driver_stats_agg_driver_id ON driver_stats_agg (driver_id);

-- Create index on driver_tier for filtering
CREATE INDEX idx_driver_stats_agg_tier ON driver_stats_agg (driver_tier);

-- Create index on rating for sorting
CREATE INDEX idx_driver_stats_agg_rating ON driver_stats_agg (rating_avg DESC);

-- Add comment
COMMENT ON MATERIALIZED VIEW driver_stats_agg IS 'Precomputed driver statistics for fast matching queries. Refreshed every 5 minutes via background job.';

-- Initial refresh
REFRESH MATERIALIZED VIEW driver_stats_agg;

-- Phase 2.3: Spatial Indices Optimization
-- Analyze and optimize PostGIS indices for high-performance geospatial queries

-- ============================================================================
-- ANALYSIS SECTION
-- ============================================================================

-- Check existing indices on stops table
SELECT
    indexname,
    indexdef
FROM
    pg_indexes
WHERE
    tablename = 'stops'
ORDER BY
    indexname;

-- Check existing indices on routes table
SELECT
    indexname,
    indexdef
FROM
    pg_indexes
WHERE
    tablename = 'routes'
ORDER BY
    indexname;

-- Check existing indices on hubs table
SELECT
    indexname,
    indexdef
FROM
    pg_indexes
WHERE
    tablename = 'hubs'
ORDER BY
    indexname;

-- ============================================================================
-- OPTIMIZATION SECTION
-- ============================================================================

-- Optimize stops location index (GIST with geography)
-- Drop old index if exists and recreate with optimizations
DROP INDEX IF EXISTS idx_stops_location;
CREATE INDEX idx_stops_location ON stops USING GIST (
    geography(location)
) WITH (fillfactor = 90);

-- Create composite index for hub_id + active status filtering
DROP INDEX IF EXISTS idx_stops_hub_active;
CREATE INDEX idx_stops_hub_active ON stops (hub_id, is_active)
WHERE is_active = true;

-- Create composite index for area filtering
DROP INDEX IF EXISTS idx_stops_area_active;
CREATE INDEX idx_stops_area_active ON stops (area_id, is_active)
WHERE is_active = true;

-- Optimize hubs location index
DROP INDEX IF EXISTS idx_hubs_location;
CREATE INDEX idx_hubs_location ON hubs USING GIST (
    geography(location)
) WITH (fillfactor = 90);

-- Create index on hub active status
DROP INDEX IF EXISTS idx_hubs_active;
CREATE INDEX idx_hubs_active ON hubs (is_active)
WHERE is_active = true;

-- Create composite index on routes for hub-based queries
DROP INDEX IF EXISTS idx_routes_hubs;
CREATE INDEX idx_routes_hubs ON routes (origin_hub_id, destination_hub_id)
WHERE status = 'active' AND seats_available > 0;

-- Create index on routes status for filtering
DROP INDEX IF EXISTS idx_routes_status_seats;
CREATE INDEX idx_routes_status_seats ON routes (status, seats_available)
WHERE status = 'active' AND seats_available > 0;

-- Create index on routes departure_time for time filtering
DROP INDEX IF EXISTS idx_routes_departure_time;
CREATE INDEX idx_routes_departure_time ON routes (departure_time);

-- Create composite index for driver routes lookup
DROP INDEX IF EXISTS idx_routes_driver_status;
CREATE INDEX idx_routes_driver_status ON routes (driver_id, status)
WHERE status = 'active';

-- ============================================================================
-- STATISTICS UPDATE
-- ============================================================================

-- Update table statistics for query planner
ANALYZE stops;
ANALYZE hubs;
ANALYZE routes;
ANALYZE route_stops;

-- ============================================================================
-- VACUUM TABLES
-- ============================================================================

-- Vacuum tables to reclaim space and update statistics
VACUUM ANALYZE stops;
VACUUM ANALYZE hubs;
VACUUM ANALYZE routes;
VACUUM ANALYZE route_stops;

-- ============================================================================
-- PERFORMANCE TESTING QUERIES
-- ============================================================================

-- Test query 1: Find nearby stops to a point
EXPLAIN ANALYZE
SELECT id, name, ST_Distance(location::geography, ST_SetSRID(ST_MakePoint(3.3792, 6.5244), 4326)::geography) as distance
FROM stops
WHERE is_active = true
AND ST_DWithin(
    location::geography,
    ST_SetSRID(ST_MakePoint(3.3792, 6.5244), 4326)::geography,
    5000  -- 5km radius
)
ORDER BY distance
LIMIT 20;

-- Test query 2: Find routes by hub pair
EXPLAIN ANALYZE
SELECT id, name, departure_time, seats_available
FROM routes
WHERE origin_hub_id = '00000000-0000-0000-0000-000000000001'::uuid
AND destination_hub_id = '00000000-0000-0000-0000-000000000002'::uuid
AND status = 'active'
AND seats_available > 0;

-- Test query 3: Find nearest hub
EXPLAIN ANALYZE
SELECT id, name, ST_Distance(location::geography, ST_SetSRID(ST_MakePoint(3.3792, 6.5244), 4326)::geography) as distance
FROM hubs
WHERE is_active = true
ORDER BY location::geography <-> ST_SetSRID(ST_MakePoint(3.3792, 6.5244), 4326)::geography
LIMIT 1;

-- ============================================================================
-- MONITORING QUERIES
-- ============================================================================

-- Check index usage statistics
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan as index_scans,
    idx_tup_read as tuples_read,
    idx_tup_fetch as tuples_fetched
FROM
    pg_stat_user_indexes
WHERE
    tablename IN ('stops', 'hubs', 'routes', 'route_stops')
ORDER BY
    idx_scan DESC;

-- Check table sizes
SELECT
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS total_size,
    pg_size_pretty(pg_relation_size(schemaname||'.'||tablename)) AS table_size,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename) - pg_relation_size(schemaname||'.'||tablename)) AS index_size
FROM
    pg_tables
WHERE
    schemaname = 'public'
    AND tablename IN ('stops', 'hubs', 'routes', 'route_stops')
ORDER BY
    pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON INDEX idx_stops_location IS 'GIST index on stops location (geography) for spatial queries with 90% fillfactor';
COMMENT ON INDEX idx_stops_hub_active IS 'Composite index for hub + active filtering on stops';
COMMENT ON INDEX idx_hubs_location IS 'GIST index on hubs location (geography) for spatial queries with 90% fillfactor';
COMMENT ON INDEX idx_routes_hubs IS 'Composite index for hub pair queries with active status filter';
COMMENT ON INDEX idx_routes_departure_time IS 'B-tree index on departure_time for time window filtering';

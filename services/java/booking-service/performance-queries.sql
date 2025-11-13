-- Performance analysis queries for booking service
-- Run these with EXPLAIN ANALYZE to verify index usage

-- Query 1: Find bookings by rider with status filter
EXPLAIN ANALYZE
SELECT * FROM bookings
WHERE rider_id = 'c0a80121-7ac0-190b-817a-c08ab0a12345'
AND status IN ('CONFIRMED', 'CHECKED_IN')
AND travel_date >= CURRENT_DATE
ORDER BY travel_date ASC, departure_time ASC
LIMIT 20;

-- Expected: Index Scan using idx_bookings_rider_status_date

-- Query 2: Seat availability for route and date
EXPLAIN ANALYZE
SELECT COUNT(*), SUM(seats_booked)
FROM bookings
WHERE route_id = 'c0a80121-7ac0-190b-817a-c08ab0a12346'
AND travel_date = '2024-12-01'
AND status IN ('CONFIRMED', 'CHECKED_IN');

-- Expected: Index Scan using idx_bookings_route_travel_status

-- Query 3: Find expired holds for cleanup
EXPLAIN ANALYZE
SELECT id, booking_reference, route_id, travel_date, seats_booked
FROM bookings
WHERE status IN ('PENDING', 'HELD')
AND expires_at < NOW()
LIMIT 100;

-- Expected: Index Scan using idx_bookings_expires_at

-- Query 4: Booking by idempotency key
EXPLAIN ANALYZE
SELECT * FROM bookings
WHERE idempotency_key = 'test-idempotency-key-12345';

-- Expected: Index Scan using idx_bookings_idempotency_key

-- Query 5: Pessimistic lock query
EXPLAIN ANALYZE
SELECT * FROM bookings
WHERE id = 'c0a80121-7ac0-190b-817a-c08ab0a12347'
FOR UPDATE;

-- Expected: Index Scan using bookings_pkey

-- Performance metrics
-- Target: All queries < 50ms P95 latency

-- Check index usage
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
AND tablename = 'bookings'
ORDER BY idx_scan DESC;

-- Check table statistics
SELECT
    relname,
    n_tup_ins,
    n_tup_upd,
    n_tup_del,
    n_live_tup,
    n_dead_tup,
    last_vacuum,
    last_autovacuum,
    last_analyze,
    last_autoanalyze
FROM pg_stat_user_tables
WHERE schemaname = 'public'
AND relname = 'bookings';

-- Cache hit ratio (should be > 95%)
SELECT
    sum(heap_blks_read) as heap_read,
    sum(heap_blks_hit) as heap_hit,
    sum(heap_blks_hit) / (sum(heap_blks_hit) + sum(heap_blks_read)) * 100 AS cache_hit_ratio
FROM pg_statio_user_tables;

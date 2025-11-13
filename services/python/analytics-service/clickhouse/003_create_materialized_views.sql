-- ========================================
-- Materialized View: Daily User Metrics
-- ========================================

CREATE MATERIALIZED VIEW IF NOT EXISTS openride_analytics.mv_daily_user_metrics
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, user_type)
AS
SELECT
    toDate(event_timestamp) AS date,
    role AS user_type,
    uniqExact(user_id) AS new_registrations,
    countIf(event_type = 'user.kyc_verified') AS kyc_verified_count,
    countIf(event_type = 'user.upgraded_to_driver') AS driver_upgrades
FROM openride_analytics.user_events
GROUP BY date, user_type;

-- ========================================
-- Materialized View: Daily Booking Metrics
-- ========================================

CREATE MATERIALIZED VIEW IF NOT EXISTS openride_analytics.mv_daily_booking_metrics
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date)
AS
SELECT
    toDate(event_timestamp) AS date,
    countIf(event_type = 'booking.created') AS bookings_created,
    countIf(event_type = 'booking.confirmed') AS bookings_confirmed,
    countIf(event_type = 'booking.cancelled') AS bookings_cancelled,
    countIf(event_type = 'booking.completed') AS bookings_completed,
    sumIf(total_price, event_type = 'booking.confirmed') AS total_booking_value,
    avgIf(total_price, event_type = 'booking.confirmed') AS avg_booking_value,
    sumIf(seats_booked, event_type = 'booking.confirmed') AS total_seats_booked
FROM openride_analytics.booking_events
GROUP BY date;

-- ========================================
-- Materialized View: Daily Payment Metrics
-- ========================================

CREATE MATERIALIZED VIEW IF NOT EXISTS openride_analytics.mv_daily_payment_metrics
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, payment_method)
AS
SELECT
    toDate(event_timestamp) AS date,
    payment_method,
    countIf(event_type = 'payment.initiated') AS payments_initiated,
    countIf(event_type = 'payment.success') AS payments_success,
    countIf(event_type = 'payment.failed') AS payments_failed,
    countIf(event_type = 'payment.refunded') AS payments_refunded,
    sumIf(amount, event_type = 'payment.success') AS total_revenue,
    avgIf(processing_time_ms, event_type = 'payment.success') AS avg_processing_time_ms,
    uniqExact(rider_id) AS unique_payers
FROM openride_analytics.payment_events
GROUP BY date, payment_method;

-- ========================================
-- Materialized View: Daily Trip Metrics
-- ========================================

CREATE MATERIALIZED VIEW IF NOT EXISTS openride_analytics.mv_daily_trip_metrics
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date)
AS
SELECT
    toDate(event_timestamp) AS date,
    countIf(event_type = 'trip.started') AS trips_started,
    countIf(event_type = 'trip.completed') AS trips_completed,
    countIf(event_type = 'trip.cancelled') AS trips_cancelled,
    avgIf(duration_minutes, event_type = 'trip.completed') AS avg_duration_minutes,
    avgIf(distance_km, event_type = 'trip.completed') AS avg_distance_km,
    sumIf(driver_earnings, event_type = 'trip.completed') AS total_driver_earnings,
    sumIf(platform_commission, event_type = 'trip.completed') AS total_platform_commission,
    countIf(on_time = true AND event_type = 'trip.completed') AS on_time_trips,
    uniqExact(driver_id) AS active_drivers
FROM openride_analytics.trip_events
GROUP BY date;

-- ========================================
-- Materialized View: Hourly Realtime Metrics
-- ========================================

CREATE MATERIALIZED VIEW IF NOT EXISTS openride_analytics.mv_hourly_realtime_metrics
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, metric_type)
AS
SELECT
    toStartOfHour(event_timestamp) AS hour,
    'user' AS metric_type,
    uniqExact(user_id) AS active_users,
    countIf(event_type = 'user.registered') AS new_users,
    0 AS metric_value
FROM openride_analytics.user_events
GROUP BY hour

UNION ALL

SELECT
    toStartOfHour(event_timestamp) AS hour,
    'booking' AS metric_type,
    0 AS active_users,
    0 AS new_users,
    count() AS metric_value
FROM openride_analytics.booking_events
WHERE event_type = 'booking.created'
GROUP BY hour

UNION ALL

SELECT
    toStartOfHour(event_timestamp) AS hour,
    'payment' AS metric_type,
    0 AS active_users,
    0 AS new_users,
    sumIf(amount, event_type = 'payment.success') AS metric_value
FROM openride_analytics.payment_events
GROUP BY hour;

-- ========================================
-- Aggregation Table: Popular Routes
-- ========================================

CREATE TABLE IF NOT EXISTS openride_analytics.agg_popular_routes
(
    date Date,
    route_id String,
    route_name String,
    origin_city String,
    destination_city String,
    driver_id String,
    total_bookings UInt32,
    total_revenue Decimal(10, 2),
    avg_occupancy Float32,
    seats_total UInt8
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, total_bookings DESC)
SETTINGS index_granularity = 8192;

-- Populate from booking events (manual refresh or scheduled job)
-- INSERT INTO openride_analytics.agg_popular_routes
-- SELECT ...

-- ========================================
-- Aggregation Table: Driver Earnings Distribution
-- ========================================

CREATE TABLE IF NOT EXISTS openride_analytics.agg_driver_earnings
(
    date Date,
    driver_id String,
    total_trips UInt32,
    total_earnings Decimal(10, 2),
    avg_earnings_per_trip Decimal(10, 2),
    total_distance_km Float64,
    active_hours Float32,
    rating_avg Float32
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, total_earnings DESC)
SETTINGS index_granularity = 8192;

-- ========================================
-- Aggregation Table: Geographic Distribution
-- ========================================

CREATE TABLE IF NOT EXISTS openride_analytics.agg_geographic_metrics
(
    date Date,
    state String,
    city String,
    metric_type LowCardinality(String),  -- 'bookings', 'revenue', 'users'
    metric_value Float64
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, state, city, metric_type)
SETTINGS index_granularity = 8192;

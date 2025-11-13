-- ========================================
-- User Events Table
-- ========================================

CREATE TABLE IF NOT EXISTS openride_analytics.user_events
(
    event_id UUID,
    event_type LowCardinality(String),
    event_timestamp DateTime64(3, 'UTC'),
    user_id String,
    phone String,
    role LowCardinality(String),
    kyc_status Nullable(LowCardinality(String)),
    city Nullable(String),
    state Nullable(String),
    metadata String,
    ingested_at DateTime64(3, 'UTC')
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_timestamp)
ORDER BY (event_type, event_timestamp, user_id)
TTL event_timestamp + INTERVAL 90 DAY DELETE
SETTINGS index_granularity = 8192;

-- ========================================
-- Booking Events Table
-- ========================================

CREATE TABLE IF NOT EXISTS openride_analytics.booking_events
(
    event_id UUID,
    event_type LowCardinality(String),
    event_timestamp DateTime64(3, 'UTC'),
    booking_id String,
    rider_id String,
    driver_id String,
    route_id String,
    seats_booked UInt8,
    total_price Decimal(10, 2),
    booking_status LowCardinality(String),
    pickup_lat Nullable(Float64),
    pickup_lon Nullable(Float64),
    dropoff_lat Nullable(Float64),
    dropoff_lon Nullable(Float64),
    scheduled_departure DateTime,
    metadata String,
    ingested_at DateTime64(3, 'UTC')
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_timestamp)
ORDER BY (event_type, event_timestamp, booking_id)
TTL event_timestamp + INTERVAL 90 DAY DELETE
SETTINGS index_granularity = 8192;

-- ========================================
-- Payment Events Table
-- ========================================

CREATE TABLE IF NOT EXISTS openride_analytics.payment_events
(
    event_id UUID,
    event_type LowCardinality(String),
    event_timestamp DateTime64(3, 'UTC'),
    payment_id String,
    booking_id String,
    rider_id String,
    amount Decimal(10, 2),
    currency LowCardinality(String),
    payment_method LowCardinality(String),
    payment_status LowCardinality(String),
    provider LowCardinality(String),
    processing_time_ms Nullable(UInt32),
    error_code Nullable(String),
    metadata String,
    ingested_at DateTime64(3, 'UTC'),
    
    INDEX idx_payment_status payment_status TYPE set(0) GRANULARITY 4
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_timestamp)
ORDER BY (event_type, event_timestamp, payment_id)
TTL event_timestamp + INTERVAL 90 DAY DELETE
SETTINGS index_granularity = 8192;

-- ========================================
-- Trip Events Table
-- ========================================

CREATE TABLE IF NOT EXISTS openride_analytics.trip_events
(
    event_id UUID,
    event_type LowCardinality(String),
    event_timestamp DateTime64(3, 'UTC'),
    trip_id String,
    booking_id String,
    driver_id String,
    rider_id String,
    route_id String,
    trip_status LowCardinality(String),
    actual_departure Nullable(DateTime),
    actual_arrival Nullable(DateTime),
    duration_minutes Nullable(UInt32),
    distance_km Nullable(Float64),
    driver_earnings Nullable(Decimal(10, 2)),
    platform_commission Nullable(Decimal(10, 2)),
    on_time Boolean DEFAULT false,
    metadata String,
    ingested_at DateTime64(3, 'UTC')
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_timestamp)
ORDER BY (event_type, event_timestamp, trip_id)
TTL event_timestamp + INTERVAL 90 DAY DELETE
SETTINGS index_granularity = 8192;

-- ========================================
-- Route Events Table
-- ========================================

CREATE TABLE IF NOT EXISTS openride_analytics.route_events
(
    event_id UUID,
    event_type LowCardinality(String),
    event_timestamp DateTime64(3, 'UTC'),
    route_id String,
    driver_id String,
    route_name String,
    origin_city String,
    destination_city String,
    origin_state String,
    destination_state String,
    seats_total UInt8,
    base_price Decimal(10, 2),
    route_status LowCardinality(String),
    metadata String,
    ingested_at DateTime64(3, 'UTC')
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_timestamp)
ORDER BY (event_type, event_timestamp, route_id)
TTL event_timestamp + INTERVAL 90 DAY DELETE
SETTINGS index_granularity = 8192;

-- ========================================
-- Location Events Table (Sampled)
-- ========================================

CREATE TABLE IF NOT EXISTS openride_analytics.location_events
(
    event_id UUID,
    event_timestamp DateTime64(3, 'UTC'),
    driver_id String,
    trip_id Nullable(String),
    lat Float64,
    lon Float64,
    speed_kmh Nullable(Float32),
    bearing Nullable(Float32),
    accuracy_meters Nullable(Float32),
    city Nullable(String),
    state Nullable(String),
    ingested_at DateTime64(3, 'UTC')
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_timestamp)
ORDER BY (driver_id, event_timestamp)
TTL event_timestamp + INTERVAL 30 DAY DELETE  -- Shorter retention for location data
SETTINGS index_granularity = 8192;

-- ========================================
-- Raw Events Table (All Kafka Events)
-- ========================================

CREATE TABLE IF NOT EXISTS openride_analytics.events_raw
(
    event_id UUID DEFAULT generateUUIDv4(),
    event_type LowCardinality(String),
    event_timestamp DateTime64(3, 'UTC'),
    entity_id String,
    entity_type LowCardinality(String),
    user_id Nullable(String),
    metadata String,  -- JSON payload
    kafka_partition UInt32,
    kafka_offset UInt64,
    kafka_timestamp DateTime64(3, 'UTC'),
    ingested_at DateTime64(3, 'UTC') DEFAULT now64(3, 'UTC'),
    
    -- Metadata indexes
    INDEX idx_entity_id entity_id TYPE bloom_filter GRANULARITY 4,
    INDEX idx_user_id user_id TYPE bloom_filter GRANULARITY 4,
    INDEX idx_metadata metadata TYPE tokenbf_v1(30720, 2, 0) GRANULARITY 4
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_timestamp)
ORDER BY (event_type, event_timestamp, entity_id)
TTL event_timestamp + INTERVAL 90 DAY DELETE
SETTINGS index_granularity = 8192;

-- ========================================
-- Comments
-- ========================================

COMMENT ON TABLE openride_analytics.events_raw IS 'Raw event stream from Kafka topics';
COMMENT ON COLUMN openride_analytics.events_raw.event_id IS 'Unique event identifier';
COMMENT ON COLUMN openride_analytics.events_raw.event_type IS 'Event type (user.registered, booking.created, etc)';
COMMENT ON COLUMN openride_analytics.events_raw.event_timestamp IS 'Event occurrence timestamp';
COMMENT ON COLUMN openride_analytics.events_raw.entity_id IS 'ID of the entity (user_id, booking_id, etc)';
COMMENT ON COLUMN openride_analytics.events_raw.metadata IS 'Full event payload as JSON';
COMMENT ON COLUMN openride_analytics.events_raw.kafka_partition IS 'Kafka partition number';
COMMENT ON COLUMN openride_analytics.events_raw.kafka_offset IS 'Kafka offset';

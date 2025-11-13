# ClickHouse Schema for Analytics Service

This directory contains ClickHouse table definitions and materialized views for the analytics service.

## Tables

### 1. events_raw
Raw event storage (all events from Kafka).

### 2. user_events
Filtered user-related events.

### 3. booking_events
Filtered booking-related events.

### 4. payment_events
Filtered payment-related events.

### 5. trip_events
Filtered trip-related events.

### 6. location_events
Sampled driver location updates.

## Materialized Views

### 1. mv_daily_user_metrics
Pre-aggregated daily user metrics (DAU, registrations, etc).

### 2. mv_daily_booking_metrics
Pre-aggregated daily booking metrics.

### 3. mv_daily_payment_metrics
Pre-aggregated daily payment metrics.

### 4. mv_hourly_realtime_metrics
Hourly metrics for real-time dashboard.

## Partitioning Strategy

- All tables partitioned by `toYYYYMM(event_timestamp)`
- Retention: 90 days hot storage, then move to cold storage
- Automatic cleanup via TTL

## Indexes

- Primary key on (`event_type`, `event_timestamp`, `entity_id`)
- Secondary indexes on frequently queried fields
- Bloom filter indexes for text search

## Usage

Execute SQL files in order:
1. `001_create_events_raw.sql`
2. `002_create_filtered_tables.sql`
3. `003_create_materialized_views.sql`
4. `004_create_aggregation_tables.sql`

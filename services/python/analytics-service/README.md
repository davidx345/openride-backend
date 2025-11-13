# Analytics Service

**Phase 8: Analytics & Reporting**

Real-time event processing and business intelligence service for OpenRide platform.

## Overview

The Analytics Service ingests event streams from Kafka, stores time-series data in ClickHouse, and provides REST APIs for dashboards, reports, and business metrics.

## Architecture

```
Kafka Topics (Events)
    ↓
Analytics Service (FastAPI + Kafka Consumer)
    ↓
ClickHouse (Time-Series Storage) + PostgreSQL (Metadata)
    ↓
Redis (Query Cache)
    ↓
Dashboard APIs (REST)
```

## Technology Stack

- **Python 3.11+**: Runtime environment
- **FastAPI 0.104+**: Async web framework for REST API
- **Confluent Kafka Python**: Kafka consumer with high performance
- **ClickHouse**: Columnar database for time-series analytics
- **PostgreSQL**: Metadata storage (report configs, scheduled jobs)
- **Redis 7+**: Query result caching
- **Pandas**: Data manipulation and aggregation
- **Celery**: Scheduled report generation
- **Pydantic**: Event schema validation
- **structlog**: Structured JSON logging

## Event Types

### User Events
- `user.registered`: New user registration
- `user.kyc_verified`: KYC verification completed
- `user.upgraded_to_driver`: Rider upgraded to driver

### Route Events
- `route.created`: Driver created new route
- `route.activated`: Route activated
- `route.cancelled`: Route cancelled

### Booking Events
- `booking.created`: New booking created
- `booking.confirmed`: Booking confirmed after payment
- `booking.cancelled`: Booking cancelled
- `booking.completed`: Trip completed

### Payment Events
- `payment.initiated`: Payment initiated
- `payment.success`: Payment successful
- `payment.failed`: Payment failed
- `payment.refunded`: Payment refunded

### Trip Events
- `trip.started`: Trip started
- `trip.completed`: Trip completed
- `trip.cancelled`: Trip cancelled

### Location Events
- `driver.location.updated`: Driver location updated (sampled)

## Key Metrics

### User Metrics
- **DAU/WAU/MAU**: Daily/Weekly/Monthly Active Users
- **User Growth Rate**: New registrations over time
- **KYC Completion Rate**: % of drivers with verified KYC
- **Driver Activation Rate**: % of registered drivers with active routes

### Booking Metrics
- **Booking Conversion Rate**: Created → Confirmed bookings
- **Cancellation Rate**: % of bookings cancelled
- **Average Booking Value**: Mean booking amount
- **Booking Frequency**: Avg bookings per user per month

### Payment Metrics
- **Payment Success Rate**: % of successful payments
- **Average Processing Time**: Mean payment completion time
- **Refund Rate**: % of payments refunded
- **Revenue**: Total successful payments

### Trip Metrics
- **Completion Rate**: % of trips completed
- **Average Trip Duration**: Mean trip time
- **Average Trip Distance**: Mean trip distance
- **On-Time Performance**: % of trips departing on time

### Driver Metrics
- **Driver Earnings Distribution**: Earnings percentiles
- **Average Earnings per Trip**: Mean driver payout
- **Active Driver Count**: Drivers with trips in period
- **Driver Retention Rate**: % of drivers active after N days

### Route Metrics
- **Popular Routes**: Most booked routes
- **Route Utilization**: Avg seat occupancy per route
- **Geographic Distribution**: Routes by city/state

## Performance Targets

- **Event Processing Latency**: < 5 seconds (p95)
- **Query Response Time**: < 500ms for dashboard APIs (p95)
- **Event Throughput**: 10,000 events/second per instance
- **Data Retention**: 90 days hot storage, 2 years cold storage
- **Cache Hit Rate**: > 80% for repeated queries

## API Endpoints

### Metrics APIs
- `GET /v1/metrics/users` - User metrics (DAU/WAU/MAU)
- `GET /v1/metrics/bookings` - Booking metrics
- `GET /v1/metrics/payments` - Payment metrics
- `GET /v1/metrics/trips` - Trip metrics
- `GET /v1/metrics/drivers` - Driver metrics
- `GET /v1/metrics/routes` - Route metrics
- `GET /v1/metrics/realtime` - Real-time dashboard metrics

### Analytics APIs
- `GET /v1/analytics/funnel` - Conversion funnel analysis
- `GET /v1/analytics/cohort` - Cohort analysis
- `GET /v1/analytics/retention` - Retention analysis
- `GET /v1/analytics/geographic` - Geographic distribution

### Export APIs
- `GET /v1/exports/csv` - Export data as CSV
- `GET /v1/exports/excel` - Export data as Excel
- `POST /v1/reports/schedule` - Schedule periodic reports
- `GET /v1/reports/{id}` - Get generated report

### Health APIs
- `GET /health` - Health check
- `GET /health/ready` - Readiness check
- `GET /health/live` - Liveness check

## Quick Start

### Local Development

```bash
# Install dependencies
cd services/python/analytics-service
pip install -e .

# Set environment variables
cp .env.example .env
# Edit .env with your configuration

# Run database migrations
alembic upgrade head

# Start Kafka consumer (separate process)
python -m app.consumer

# Start FastAPI server
uvicorn app.main:app --host 0.0.0.0 --port 8097 --reload
```

### Docker

```bash
# Build image
docker build -t openride/analytics-service:latest .

# Run with docker-compose
docker-compose up analytics-service
```

### Testing

```bash
# Run all tests
pytest

# Run with coverage
pytest --cov=app --cov-report=html

# Run specific test
pytest tests/test_aggregations.py -v
```

## Configuration

See `.env.example` for all configuration options.

Key environment variables:
- `DATABASE_URL`: PostgreSQL connection string
- `CLICKHOUSE_URL`: ClickHouse connection string
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka brokers
- `REDIS_URL`: Redis connection string
- `CONSUMER_GROUP_ID`: Kafka consumer group

## Data Flow

1. **Event Ingestion**: Kafka consumer reads events from topics
2. **Validation**: Pydantic schemas validate event structure
3. **Storage**: Events stored in ClickHouse (time-series)
4. **Aggregation**: Materialized views pre-compute metrics
5. **Caching**: Frequently accessed metrics cached in Redis
6. **API**: FastAPI serves metrics to dashboards
7. **Export**: Scheduled reports generated and emailed

## Monitoring

- **Prometheus Metrics**: Exposed on `/metrics`
- **Health Checks**: `/health/*` endpoints
- **Structured Logs**: JSON format to stdout
- **Kafka Consumer Lag**: Monitored via consumer group metrics

## Scaling

- **Horizontal Scaling**: Add more consumer instances (Kafka consumer groups)
- **ClickHouse Sharding**: Partition data by date or entity type
- **Redis Clustering**: Use Redis Cluster for cache
- **Celery Workers**: Scale report generation workers independently

## License

Proprietary - OpenRide Platform

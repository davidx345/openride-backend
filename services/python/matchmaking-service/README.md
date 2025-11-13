# OpenRide Matchmaking Service

Intelligent route matching and ranking engine for OpenRide platform.

## Overview

The Matchmaking Service is the AI/ML core of OpenRide's Phase 3 implementation. It provides intelligent matching between riders and routes using:

- **Geospatial filtering** with PostGIS
- **Multi-factor scoring algorithm**
- **Real-time ranking and explanations**
- **Performance optimization** (P95 < 200ms target)

## Features

### Core Capabilities

✅ **Geospatial Search**
- Find routes with stops near rider's origin/destination
- ST_DWithin for efficient spatial queries
- Configurable search radius (default: 5km)

✅ **Intelligent Scoring**
- Route Match (40%): Exact/partial coverage
- Time Match (30%): Departure time proximity
- Driver Rating (20%): User ratings
- Price (10%): Competitive pricing

✅ **Performance Optimization**
- Redis caching for hot routes
- Spatial indexes (GIST)
- Async processing
- P95 latency < 200ms

✅ **Rich Explanations**
- Human-readable match reasons
- Score breakdowns
- Recommendation flags

## Architecture

```
matchmaking-service/
├── app/
│   ├── main.py                  # FastAPI application
│   ├── core/
│   │   ├── config.py            # Settings
│   │   ├── database.py          # SQLAlchemy async
│   │   ├── cache.py             # Redis manager
│   │   ├── security.py          # JWT validation
│   │   ├── logging_config.py    # JSON logging
│   │   └── exceptions.py        # Custom exceptions
│   ├── models/
│   │   ├── route.py             # Route with geospatial
│   │   ├── stop.py              # Stop waypoints
│   │   ├── route_stop.py        # Association
│   │   └── mixins.py            # Timestamp mixin
│   ├── schemas/
│   │   └── matching.py          # Request/response schemas
│   ├── repositories/
│   │   └── route_repository.py  # Geospatial queries
│   ├── services/
│   │   ├── matching_service.py  # Main orchestration
│   │   ├── scoring_service.py   # Composite scoring
│   │   ├── geospatial_utils.py  # Spatial utilities
│   │   └── user_service.py      # External API client
│   └── api/
│       └── v1/
│           └── matching.py      # POST /v1/match
├── alembic/
│   └── versions/
│       └── 001_add_geospatial_columns.py
├── tests/
├── pyproject.toml
├── Dockerfile
└── README.md
```

## Quick Start

### Prerequisites

- Python 3.11+
- Poetry 1.7+
- PostgreSQL 14+ with PostGIS 3.3+
- Redis 7+

### Installation

```bash
# Install dependencies
poetry install

# Copy environment file
cp .env.example .env

# Edit .env with your configuration
nano .env
```

### Database Migration

```bash
# Run migration to add geospatial columns
poetry run alembic upgrade head
```

### Running the Service

```bash
# Development mode
poetry run uvicorn app.main:app --reload --port 8084

# Production mode
poetry run uvicorn app.main:app --host 0.0.0.0 --port 8084 --workers 4
```

### Docker

```bash
# Build image
docker build -t openride/matchmaking-service:latest .

# Run container
docker run -d \
  -p 8084:8084 \
  -e DATABASE_URL=postgresql+asyncpg://user:pass@host:5432/openride \
  -e REDIS_URL=redis://host:6379/1 \
  --name matchmaking-service \
  openride/matchmaking-service:latest
```

## API Documentation

### POST /v1/match

Match riders to compatible routes with intelligent ranking.

**Request:**
```json
{
  "rider_id": "uuid",
  "origin_lat": 6.4302,
  "origin_lon": 3.5066,
  "dest_lat": 6.4281,
  "dest_lon": 3.4219,
  "desired_time": "07:00:00",
  "max_price": 2000.00,
  "min_seats": 1,
  "radius_km": 5.0
}
```

**Response:**
```json
{
  "matches": [
    {
      "route_id": "uuid",
      "driver_id": "uuid",
      "final_score": 0.92,
      "scores": {
        "route_match": 1.0,
        "time_match": 0.95,
        "rating": 0.96,
        "price": 0.80
      },
      "explanation": "✓ Exact match: Route covers both origin and destination | ✓ Great timing: Departs in 5 min | ✓ Highly rated driver: 4.8/5.0 ⭐",
      "recommended": true,
      "route_name": "Lekki to VI Express",
      "departure_time": "07:05:00",
      "seats_available": 3,
      "base_price": 1500.00,
      "driver_rating": 4.8
    }
  ],
  "total_candidates": 45,
  "matched_candidates": 12,
  "execution_time_ms": 87
}
```

### GET /health

Health check endpoint.

**Response:**
```json
{
  "status": "healthy",
  "service": "matchmaking-service"
}
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL connection URL | Required |
| `REDIS_URL` | Redis connection URL | Required |
| `SERVICE_PORT` | Service port | 8084 |
| `DEFAULT_SEARCH_RADIUS_KM` | Default search radius | 5.0 |
| `TIME_WINDOW_MINUTES` | Time matching window | 15 |
| `WEIGHT_ROUTE_MATCH` | Route match weight | 0.4 |
| `WEIGHT_TIME_MATCH` | Time match weight | 0.3 |
| `WEIGHT_RATING` | Rating weight | 0.2 |
| `WEIGHT_PRICE` | Price weight | 0.1 |
| `PERFORMANCE_TARGET_MS` | Target latency | 200 |

### Scoring Weights

Weights must sum to 1.0. Default configuration:

- **Route Match (40%)**: Exact/partial/none
- **Time Match (30%)**: Within ±15 minutes
- **Rating (20%)**: 0-5 star scale
- **Price (10%)**: Relative to other matches

## Development

### Running Tests

```bash
# Run all tests
poetry run pytest

# With coverage
poetry run pytest --cov=app --cov-report=term-missing

# Specific test file
poetry run pytest tests/test_scoring.py -v
```

### Code Quality

```bash
# Format code
poetry run black app/ tests/

# Lint
poetry run ruff app/ tests/

# Type check
poetry run mypy app/
```

### Database Operations

```bash
# Create new migration
poetry run alembic revision --autogenerate -m "description"

# Upgrade to latest
poetry run alembic upgrade head

# Downgrade one version
poetry run alembic downgrade -1

# Show current version
poetry run alembic current
```

## Performance

### Optimization Strategies

1. **Database**
   - GIST spatial indexes on geometry columns
   - Composite indexes on (status, departure_time)
   - Connection pooling (10-50 connections)

2. **Caching**
   - Hot routes: 5min TTL
   - Search results: 3min TTL
   - Active routes list: 1min TTL

3. **Query Optimization**
   - Limit candidates to 50 routes max
   - Eager load route_stops with selectinload
   - Use geography type for accurate distance

4. **Async Processing**
   - All DB queries async
   - Parallel driver data fetching
   - Non-blocking Redis operations

### Performance Targets

- **P50 Latency**: < 100ms
- **P95 Latency**: < 200ms
- **P99 Latency**: < 500ms
- **Throughput**: > 100 req/s

## Troubleshooting

### Common Issues

**1. Slow queries**
```bash
# Check if spatial indexes exist
psql -d openride -c "\d routes"

# Rebuild indexes
psql -d openride -c "REINDEX INDEX idx_routes_start_location;"
```

**2. Redis connection errors**
```bash
# Check Redis connectivity
redis-cli -h localhost -p 6379 ping

# View cache keys
redis-cli -h localhost -p 6379 keys "routes:*"
```

**3. High memory usage**
```bash
# Adjust connection pool size in .env
MAX_DB_CONNECTIONS=30
MIN_DB_CONNECTIONS=5
```

## Monitoring

### Health Checks

```bash
# Service health
curl http://localhost:8084/health

# API docs
open http://localhost:8084/docs
```

### Logs

```bash
# View logs (JSON format)
docker logs -f matchmaking-service

# Filter by level
docker logs matchmaking-service 2>&1 | grep '"level":"ERROR"'
```

## Contributing

Follow the constraints in `constraints.md`:

- Max 500 lines per file
- PEP8 compliance
- Async/await patterns
- 80%+ test coverage
- Comprehensive docstrings

## License

Proprietary - OpenRide Platform

---

**Service**: Matchmaking Service  
**Port**: 8084  
**Version**: 1.0.0  
**Phase**: 3 - Search, Discovery & Matching Engine

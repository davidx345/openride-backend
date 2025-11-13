# OpenRide Search & Discovery Service

Public-facing API for searching and discovering available carpool routes.

## Overview

The Search & Discovery Service provides riders with a simple, fast API to find available routes based on location and time preferences.

## Features

✅ **Route Search**
- Search by origin latitude/longitude
- Optional destination filtering
- Time-based filtering
- Configurable search radius (0.1-20km)
- Pagination support

✅ **Route Details**
- Get complete route information
- View all stops with pricing
- Check seat availability
- See departure times and schedule

✅ **Performance**
- Redis caching (3min TTL)
- Optimized PostGIS queries
- Fast response times
- Scalable architecture

## Architecture

```
search-service/
├── app/
│   ├── main.py              # FastAPI application
│   ├── core/
│   │   ├── config.py        # Settings
│   │   ├── database.py      # SQLAlchemy async
│   │   ├── cache.py         # Redis cache
│   │   └── logging_config.py
│   └── api/
│       └── v1/
│           └── routes.py    # Search endpoints
├── pyproject.toml
├── Dockerfile
└── README.md
```

## Quick Start

### Installation

```bash
# Install dependencies
poetry install

# Copy environment file
cp .env.example .env
```

### Running

```bash
# Development
poetry run uvicorn app.main:app --reload --port 8085

# Production
poetry run uvicorn app.main:app --host 0.0.0.0 --port 8085 --workers 4
```

### Docker

```bash
docker build -t openride/search-service:latest .
docker run -d -p 8085:8085 --name search-service openride/search-service:latest
```

## API Endpoints

### GET /v1/routes

Search for routes by location and time.

**Query Parameters:**
- `lat` (required): Origin latitude
- `lng` (required): Origin longitude
- `dest_lat` (optional): Destination latitude
- `dest_lng` (optional): Destination longitude
- `time` (optional): Desired time (HH:MM:SS)
- `radius` (optional): Search radius in km (default: 5.0)
- `limit` (optional): Max results (default: 20)
- `offset` (optional): Pagination offset (default: 0)

**Example:**
```bash
curl "http://localhost:8085/v1/routes?lat=6.4302&lng=3.5066&radius=5.0&limit=20"
```

**Response:**
```json
{
  "results": [
    {
      "id": "uuid",
      "name": "Lekki to VI Express",
      "driver_id": "uuid",
      "departure_time": "07:00:00",
      "seats_available": 3,
      "base_price": 1500.00,
      "stops": [
        {
          "id": "uuid",
          "name": "Lekki Phase 1",
          "lat": 6.4302,
          "lon": 3.5066,
          "stop_order": 0,
          "price_from_origin": 0.00
        }
      ]
    }
  ],
  "total": 15,
  "limit": 20,
  "offset": 0,
  "has_more": false
}
```

### GET /v1/routes/{route_id}

Get detailed route information.

**Example:**
```bash
curl "http://localhost:8085/v1/routes/{route_id}"
```

**Response:**
```json
{
  "id": "uuid",
  "driver_id": "uuid",
  "vehicle_id": "uuid",
  "name": "Lekki to VI Express",
  "departure_time": "07:00:00",
  "active_days": [0, 1, 2, 3, 4],
  "seats_total": 4,
  "seats_available": 3,
  "base_price": 1500.00,
  "status": "ACTIVE",
  "stops": [...]
}
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL URL | Required |
| `REDIS_URL` | Redis URL | Required |
| `SERVICE_PORT` | Service port | 8085 |
| `DEFAULT_SEARCH_RADIUS_KM` | Default radius | 5.0 |
| `MAX_SEARCH_RADIUS_KM` | Max radius | 20.0 |
| `DEFAULT_PAGE_SIZE` | Default results | 20 |
| `MAX_PAGE_SIZE` | Max results | 100 |
| `REDIS_CACHE_TTL` | Cache TTL (seconds) | 180 |

## Development

### Code Quality

```bash
poetry run black app/
poetry run ruff app/
poetry run mypy app/
```

## Monitoring

```bash
# Health check
curl http://localhost:8085/health

# API docs
open http://localhost:8085/docs
```

## License

Proprietary - OpenRide Platform

---

**Service**: Search & Discovery Service  
**Port**: 8085  
**Version**: 1.0.0  
**Phase**: 3 - Search, Discovery & Matching Engine

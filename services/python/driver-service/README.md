# Driver Service

OpenRide Driver Service - Route and Vehicle Management

## Overview

The Driver Service manages driver vehicles and fixed routes. Drivers create predetermined routes with ordered stops, schedules, and pricing.

## Features

- Vehicle CRUD operations
- Route management with ordered stops
- Geospatial stop deduplication using PostGIS
- Schedule management with RRULE support
- Seat availability tracking
- KYC verification integration
- Rate limiting for route creation

## Tech Stack

- **Framework**: FastAPI 0.109+
- **Language**: Python 3.11+
- **Database**: PostgreSQL 14+ with PostGIS
- **ORM**: SQLAlchemy 2.0 (async)
- **Migrations**: Alembic
- **Validation**: Pydantic v2
- **Cache**: Redis 7+

## Prerequisites

- Python 3.11+
- PostgreSQL 14+ with PostGIS extension
- Redis 7+
- Poetry (for dependency management)

## Setup

### 1. Install Dependencies

```bash
poetry install
```

### 2. Configure Environment

```bash
cp .env.example .env
# Edit .env with your configuration
```

### 3. Run Database Migrations

```bash
poetry run alembic upgrade head
```

### 4. Run the Service

Development mode:
```bash
poetry run uvicorn app.main:app --reload --port 8082
```

Production mode:
```bash
poetry run uvicorn app.main:app --host 0.0.0.0 --port 8082 --workers 4
```

## API Endpoints

### Vehicles

- `POST /v1/drivers/vehicles` - Create vehicle
- `GET /v1/drivers/vehicles` - Get driver's vehicles
- `GET /v1/drivers/vehicles/{id}` - Get vehicle by ID
- `PUT /v1/drivers/vehicles/{id}` - Update vehicle
- `DELETE /v1/drivers/vehicles/{id}` - Delete vehicle

### Routes

- `POST /v1/drivers/routes` - Create route
- `GET /v1/drivers/routes` - Get driver's routes
- `GET /v1/drivers/routes/active` - Get active routes
- `GET /v1/drivers/routes/{id}` - Get route by ID
- `PUT /v1/drivers/routes/{id}` - Update route
- `PATCH /v1/drivers/routes/{id}/status` - Update route status
- `DELETE /v1/drivers/routes/{id}` - Delete route

### Stops

- `GET /v1/stops` - Search stops by proximity
- `GET /v1/stops/{id}` - Get stop by ID

## Database Schema

### Vehicles Table
- Vehicle registration and details
- Driver ownership
- Capacity information

### Routes Table
- Route metadata
- Schedule and pricing
- Seat availability
- Status (ACTIVE, PAUSED, CANCELLED)

### Stops Table
- Geographic waypoints
- PostGIS location for spatial queries
- Name and landmark information

### Route_Stops Table
- Junction table for route-stop relationship
- Stop ordering
- Arrival time offsets
- Segment pricing

## Testing

Run tests:
```bash
poetry run pytest
```

Run with coverage:
```bash
poetry run pytest --cov=app --cov-report=html
```

## Docker

Build image:
```bash
docker build -t openride/driver-service:latest .
```

Run container:
```bash
docker run -p 8082:8082 --env-file .env openride/driver-service:latest
```

## Documentation

API documentation available at:
- Swagger UI: http://localhost:8082/docs
- ReDoc: http://localhost:8082/redoc

## Architecture

```
app/
├── api/           # API endpoints
├── core/          # Core utilities (config, database, security)
├── models/        # SQLAlchemy models
├── repositories/  # Data access layer
├── schemas/       # Pydantic schemas
├── services/      # Business logic layer
└── main.py        # Application entry point
```

## Development Guidelines

- Follow PEP 8 style guide
- Use type hints
- Write comprehensive docstrings
- Add tests for new features
- Use async/await throughout
- Keep functions small and focused

## License

Copyright © 2024 OpenRide

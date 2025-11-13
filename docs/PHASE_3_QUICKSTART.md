# Phase 3 Quick Start Guide

Get Phase 3 services (Search & Matchmaking) up and running in 5 minutes.

## Prerequisites

✅ Phase 2 (Driver Service) completed  
✅ PostgreSQL 14+ with PostGIS running  
✅ Redis 7+ running  
✅ Python 3.11+ installed  
✅ Poetry 1.7+ installed

---

## Step 1: Run Database Migration

The migration adds geospatial columns to the `routes` table.

```bash
cd services/python/matchmaking-service

# Install dependencies
poetry install

# Copy environment file
cp .env.example .env

# Edit .env file with your database URL
nano .env

# Run migration
poetry run alembic upgrade head
```

**Expected Output**:
```
INFO  [alembic.runtime.migration] Running upgrade  -> 001, Add geospatial columns to routes table
```

**Verification**:
```bash
psql -d openride -c "\d routes"
```

You should see new columns:
- `start_lat`, `start_lon`
- `end_lat`, `end_lon`
- `start_location` (geometry)
- `end_location` (geometry)

---

## Step 2: Start Matchmaking Service

```bash
cd services/python/matchmaking-service

# Ensure .env is configured
cat .env

# Start service
poetry run uvicorn app.main:app --reload --port 8084
```

**Expected Output**:
```
INFO:     Started server process
INFO:     Waiting for application startup.
INFO:     Application startup complete.
INFO:     Uvicorn running on http://127.0.0.1:8084
```

**Test Health**:
```bash
curl http://localhost:8084/health
# {"status":"healthy","service":"matchmaking-service"}
```

**View API Docs**:
```
http://localhost:8084/docs
```

---

## Step 3: Start Search Service

Open a new terminal:

```bash
cd services/python/search-service

# Install dependencies
poetry install

# Copy environment file
cp .env.example .env

# Edit .env
nano .env

# Start service
poetry run uvicorn app.main:app --reload --port 8085
```

**Expected Output**:
```
INFO:     Started server process
INFO:     Waiting for application startup.
INFO:     Application startup complete.
INFO:     Uvicorn running on http://127.0.0.1:8085
```

**Test Health**:
```bash
curl http://localhost:8085/health
# {"status":"healthy","service":"search-service"}
```

---

## Step 4: Test the APIs

### Test Route Search

```bash
# Search for routes near a location
curl -X GET "http://localhost:8085/v1/routes?lat=6.4302&lng=3.5066&radius=5.0&limit=10"
```

**Expected Response**:
```json
{
  "results": [
    {
      "id": "uuid",
      "name": "Route Name",
      "driver_id": "uuid",
      "departure_time": "07:00:00",
      "seats_available": 3,
      "base_price": 1500.00,
      "stops": [...]
    }
  ],
  "total": 5,
  "limit": 10,
  "offset": 0,
  "has_more": false
}
```

### Test Route Matching

```bash
# Match a rider to routes
curl -X POST "http://localhost:8084/v1/match" \
  -H "Content-Type: application/json" \
  -d '{
    "rider_id": "550e8400-e29b-41d4-a716-446655440000",
    "origin_lat": 6.4302,
    "origin_lon": 3.5066,
    "dest_lat": 6.4281,
    "dest_lon": 3.4219,
    "desired_time": "07:00:00",
    "max_price": 2000.00,
    "min_seats": 1,
    "radius_km": 5.0
  }'
```

**Expected Response**:
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
      "explanation": "✓ Exact match: Route covers both origin and destination | ✓ Great timing: Departs in 5 min",
      "recommended": true
    }
  ],
  "total_candidates": 45,
  "matched_candidates": 12,
  "execution_time_ms": 87
}
```

---

## Step 5: Verify Redis Caching

```bash
# Connect to Redis
redis-cli

# Check cached keys
KEYS routes:*
KEYS search:*

# View cache content
GET routes:active

# Monitor cache activity
MONITOR
```

---

## Running All Services Together

Use the provided script:

```bash
# From project root
bash start-phase3-services.sh
```

This will start:
1. Matchmaking Service (port 8084)
2. Search Service (port 8085)

---

## Quick Troubleshooting

### Issue: Migration fails with "relation already exists"

**Solution**: Migration already ran. Check with:
```bash
poetry run alembic current
```

To rollback:
```bash
poetry run alembic downgrade -1
```

### Issue: "Redis connection failed"

**Check Redis**:
```bash
redis-cli ping
# Expected: PONG
```

**Start Redis**:
```bash
# Ubuntu/Debian
sudo systemctl start redis

# macOS
brew services start redis

# Docker
docker run -d -p 6379:6379 redis:7-alpine
```

### Issue: "Database connection error"

**Check PostgreSQL**:
```bash
psql -d openride -c "SELECT version();"
```

**Check PostGIS**:
```bash
psql -d openride -c "SELECT PostGIS_Version();"
```

**Install PostGIS if missing**:
```bash
# Ubuntu/Debian
sudo apt-get install postgresql-14-postgis-3

# macOS
brew install postgis

# Enable in database
psql -d openride -c "CREATE EXTENSION IF NOT EXISTS postgis;"
```

### Issue: Port already in use

**Find process**:
```bash
# Linux/macOS
lsof -i :8084
lsof -i :8085

# Windows
netstat -ano | findstr :8084
```

**Kill process**:
```bash
kill -9 <PID>
```

### Issue: No routes found in search

**Check data**:
```bash
psql -d openride -c "SELECT COUNT(*) FROM routes WHERE status = 'ACTIVE';"
psql -d openride -c "SELECT COUNT(*) FROM route_stops;"
```

**Verify geospatial data**:
```bash
psql -d openride -c "SELECT COUNT(*) FROM routes WHERE start_location IS NOT NULL;"
```

If 0, the migration didn't populate existing routes. Run:
```bash
poetry run alembic downgrade -1
poetry run alembic upgrade head
```

---

## Environment Configuration

### Matchmaking Service (.env)

```bash
DATABASE_URL=postgresql+asyncpg://openride:openride@localhost:5432/openride
REDIS_URL=redis://localhost:6379/1
SECRET_KEY=your-secret-key-change-in-production
USER_SERVICE_URL=http://localhost:8081
DRIVER_SERVICE_URL=http://localhost:8082
```

### Search Service (.env)

```bash
DATABASE_URL=postgresql+asyncpg://openride:openride@localhost:5432/openride
REDIS_URL=redis://localhost:6379/2
MATCHMAKING_SERVICE_URL=http://localhost:8084
```

---

## Performance Check

### Latency Test

```bash
# Install Apache Bench
# Ubuntu: sudo apt-get install apache2-utils
# macOS: already installed

# Test search endpoint
ab -n 100 -c 10 "http://localhost:8085/v1/routes?lat=6.43&lng=3.51"

# Look for "Time per request" - should be < 200ms
```

### Cache Hit Rate

```bash
redis-cli INFO stats | grep keyspace_hits
redis-cli INFO stats | grep keyspace_misses
```

Calculate hit rate:
```
hit_rate = hits / (hits + misses)
```

Target: > 70% hit rate

---

## Next Steps

1. **Create test data**:
   - Add sample routes with geospatial data
   - Create sample stops
   - Link routes to stops

2. **Test matching scenarios**:
   - Exact matches (origin + destination)
   - Partial matches (origin only)
   - No matches
   - Time filtering
   - Price filtering

3. **Monitor performance**:
   - Check execution times
   - Verify cache effectiveness
   - Profile slow queries

4. **Write tests**:
   - Unit tests for scoring
   - Integration tests for APIs
   - Performance benchmarks

---

## Useful Commands

```bash
# View logs
docker logs -f matchmaking-service
docker logs -f search-service

# Restart services
docker restart matchmaking-service search-service

# Check database migrations
poetry run alembic history
poetry run alembic current

# Format code
poetry run black app/

# Run tests
poetry run pytest -v

# Check coverage
poetry run pytest --cov=app --cov-report=html
```

---

## Success Checklist

- [ ] PostgreSQL with PostGIS running
- [ ] Redis running
- [ ] Migration applied successfully
- [ ] Matchmaking service started (port 8084)
- [ ] Search service started (port 8085)
- [ ] Health checks passing
- [ ] Search API returns results
- [ ] Match API returns ranked routes
- [ ] Redis cache working
- [ ] Execution time < 200ms

**Status**: Phase 3 services running! 🚀

For detailed documentation, see:
- `services/python/matchmaking-service/README.md`
- `services/python/search-service/README.md`
- `docs/PHASE_3_COMPLETE.md`

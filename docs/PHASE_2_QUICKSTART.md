# Phase 2 - Driver Service Quick Start

## Prerequisites

Before running the Driver Service, ensure you have:

1. **Python 3.11+** installed
2. **Poetry** installed: `curl -sSL https://install.python-poetry.org | python3 -`
3. **PostgreSQL 14+** with PostGIS extension running
4. **Redis 7+** running (optional for caching)

## Database Setup

### Option 1: Using Docker Compose (Recommended)

From the root directory:

```bash
docker-compose up -d postgres redis
```

This starts PostgreSQL with PostGIS and Redis.

### Option 2: Manual PostgreSQL Setup

1. Install PostgreSQL and PostGIS:
   ```bash
   # Ubuntu/Debian
   sudo apt-get install postgresql-14 postgresql-14-postgis-3
   
   # macOS
   brew install postgresql@14 postgis
   ```

2. Create database and enable PostGIS:
   ```sql
   CREATE DATABASE openride;
   \c openride
   CREATE EXTENSION postgis;
   ```

3. Create user:
   ```sql
   CREATE USER openride WITH PASSWORD 'openride';
   GRANT ALL PRIVILEGES ON DATABASE openride TO openride;
   ```

## Quick Start

### 1. Navigate to Driver Service

```bash
cd services/python/driver-service
```

### 2. Install Dependencies

```bash
poetry install
```

### 3. Configure Environment

```bash
cp .env.example .env
```

Edit `.env` and update database credentials if needed:

```env
DATABASE_URL=postgresql+asyncpg://openride:openride@localhost:5432/openride
REDIS_URL=redis://localhost:6379/0
JWT_SECRET_KEY=your-secret-key-change-in-production
```

### 4. Run Database Migrations

```bash
poetry run alembic upgrade head
```

This creates all tables:
- `vehicles`
- `stops` (with PostGIS)
- `routes`
- `route_stops`

### 5. Start the Service

**Linux/macOS:**
```bash
./start.sh
```

**Windows:**
```cmd
start.bat
```

**Or manually:**
```bash
poetry run uvicorn app.main:app --reload --port 8082
```

The service will start on **http://localhost:8082**

### 6. Verify Installation

Open your browser and visit:

- **API Docs**: http://localhost:8082/docs
- **Health Check**: http://localhost:8082/health

You should see:
```json
{
  "status": "healthy",
  "service": "driver-service"
}
```

## Testing the API

### 1. Get a JWT Token

For testing, you'll need a JWT token. You can use the Auth Service (if Phase 1 is complete) or create a test token.

**Quick Test Token (Development Only):**

```python
from jose import jwt
from uuid import uuid4
from datetime import datetime, timedelta

payload = {
    "sub": str(uuid4()),
    "role": "DRIVER",
    "phone": "+2348012345678",
    "exp": datetime.utcnow() + timedelta(hours=1)
}

token = jwt.encode(payload, "changeme", algorithm="HS256")
print(token)
```

### 2. Create a Vehicle

```bash
curl -X POST http://localhost:8082/v1/drivers/vehicles \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "plate_number": "ABC-123DE",
    "make": "Toyota",
    "model": "Camry",
    "year": 2020,
    "color": "Silver",
    "seats_total": 4
  }'
```

### 3. Create a Route

```bash
curl -X POST http://localhost:8082/v1/drivers/routes \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "vehicle_id": "VEHICLE_ID_FROM_STEP_2",
    "name": "Lekki to Victoria Island",
    "departure_time": "07:00:00",
    "active_days": [0, 1, 2, 3, 4],
    "seats_total": 3,
    "base_price": "1500.00",
    "stops": [
      {
        "name": "Lekki Phase 1",
        "lat": "6.4302",
        "lon": "3.5066",
        "planned_arrival_offset_minutes": 0,
        "price_from_origin": "0.00"
      },
      {
        "name": "Victoria Island",
        "lat": "6.4281",
        "lon": "3.4219",
        "planned_arrival_offset_minutes": 30,
        "price_from_origin": "1500.00"
      }
    ]
  }'
```

### 4. Search Stops

```bash
curl "http://localhost:8082/v1/stops?lat=6.4302&lon=3.5066&radius_km=5&limit=10"
```

## Running Tests

```bash
# Run all tests
poetry run pytest

# Run with coverage
poetry run pytest --cov=app --cov-report=html

# Run specific test file
poetry run pytest tests/test_vehicles.py

# View coverage report
open htmlcov/index.html  # macOS
xdg-open htmlcov/index.html  # Linux
```

## Docker Deployment

### Build Image

```bash
docker build -t openride/driver-service:latest .
```

### Run Container

```bash
docker run -p 8082:8082 \
  -e DATABASE_URL=postgresql+asyncpg://openride:openride@host.docker.internal:5432/openride \
  -e REDIS_URL=redis://host.docker.internal:6379/0 \
  openride/driver-service:latest
```

## Troubleshooting

### Database Connection Error

**Error:** `could not connect to server`

**Solution:**
- Ensure PostgreSQL is running: `docker-compose ps` or `systemctl status postgresql`
- Check DATABASE_URL in `.env`
- Verify database exists: `psql -U openride -d openride`

### PostGIS Extension Missing

**Error:** `type "geometry" does not exist`

**Solution:**
```sql
\c openride
CREATE EXTENSION postgis;
```

### Poetry Not Found

**Error:** `poetry: command not found`

**Solution:**
```bash
curl -sSL https://install.python-poetry.org | python3 -
export PATH="$HOME/.local/bin:$PATH"
```

### Port Already in Use

**Error:** `Address already in use`

**Solution:**
```bash
# Find process using port 8082
lsof -i :8082  # macOS/Linux
netstat -ano | findstr :8082  # Windows

# Kill process or use different port
poetry run uvicorn app.main:app --reload --port 8083
```

### JWT Token Invalid

**Error:** `Could not validate credentials`

**Solution:**
- Ensure JWT_SECRET_KEY in `.env` matches Auth Service
- Check token expiration
- Verify token format: `Bearer <token>`

## Development Tips

### Hot Reload

The service runs with `--reload` flag, so code changes auto-restart the server.

### Database Migrations

After modifying models, create a new migration:

```bash
# Auto-generate migration
poetry run alembic revision --autogenerate -m "Description"

# Apply migration
poetry run alembic upgrade head

# Rollback migration
poetry run alembic downgrade -1
```

### Interactive API Testing

1. Open http://localhost:8082/docs
2. Click "Authorize" button
3. Enter: `Bearer YOUR_TOKEN`
4. Test endpoints interactively

### Database Shell

```bash
# Connect to PostgreSQL
psql -U openride -d openride

# View tables
\dt

# View routes with stops
SELECT r.name, s.name, rs.stop_order 
FROM routes r 
JOIN route_stops rs ON r.id = rs.route_id 
JOIN stops s ON rs.stop_id = s.id 
ORDER BY r.id, rs.stop_order;
```

### Logs

Logs are in JSON format. To view pretty-printed:

```bash
poetry run uvicorn app.main:app --reload --port 8082 2>&1 | jq
```

## Next Steps

Once the Driver Service is running:

1. ✅ Create some test vehicles and routes
2. ✅ Verify PostGIS geospatial queries work
3. ✅ Run the test suite
4. 🚀 Proceed to **Phase 3**: Matchmaking Service

## Support

For issues or questions:
- Check the logs: `tail -f logs/driver-service.log`
- Review the README: `README.md`
- Check the API docs: http://localhost:8082/docs

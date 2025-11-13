# Fleet/Monitoring Service

Real-time driver location tracking and trip monitoring service for OpenRide.

## Overview

The Fleet Service provides real-time tracking capabilities using WebSocket (Socket.IO) for live updates and REST APIs for trip management. It handles:

- **Driver Location Tracking** - GPS updates every 5 seconds
- **Trip Status Updates** - Real-time trip lifecycle management
- **Rider Subscriptions** - Riders receive live driver location updates
- **Connection Management** - Session tracking with JWT authentication
- **Horizontal Scaling** - Redis adapter for multi-instance deployments

## Technology Stack

- **FastAPI 0.104+** - REST API framework
- **Socket.IO 5.10+** - WebSocket server with fallback
- **SQLAlchemy 2.0** - Async ORM
- **PostgreSQL + PostGIS** - Geospatial data storage
- **Redis 7+** - Pub/Sub for broadcasting + session storage
- **Python 3.11+** - Runtime

## Architecture

```
┌─────────────┐     WebSocket      ┌──────────────┐
│   Driver    │◄──────────────────►│              │
│   Mobile    │    (Socket.IO)     │    Fleet     │
└─────────────┘                    │   Service    │
                                   │              │
┌─────────────┐     Subscribe      │  (Socket.IO  │
│    Rider    │◄──────────────────►│   + FastAPI) │
│   Mobile    │    (Socket.IO)     │              │
└─────────────┘                    └──────┬───────┘
                                          │
                    ┌─────────────────────┼─────────────────┐
                    │                     │                 │
                    v                     v                 v
             ┌──────────┐         ┌──────────┐      ┌──────────┐
             │PostgreSQL│         │  Redis   │      │ Booking  │
             │ +PostGIS │         │ Pub/Sub  │      │ Service  │
             └──────────┘         └──────────┘      └──────────┘
```

## Quick Start

### Local Development

```bash
# Install dependencies
cd services/python/fleet-service
pip install -e .

# Run migrations
alembic upgrade head

# Start server
uvicorn app.main:app --reload --port 8096
```

### Docker

```bash
# Build and start
docker-compose up fleet-service

# Health check
curl http://localhost:8096/health
```

## WebSocket Events

### Driver Events

**Connect**:
```javascript
const socket = io('http://localhost:8096', {
  auth: { token: 'JWT_TOKEN' }
});
```

**Send Location**:
```javascript
socket.emit('driver:location', {
  latitude: 6.5244,
  longitude: 3.3792,
  bearing: 45.5,
  speed: 25.3,
  accuracy: 10.2
});
```

**Go Online/Offline**:
```javascript
socket.emit('driver:online', { available: true });
socket.emit('driver:offline');
```

### Rider Events

**Subscribe to Driver**:
```javascript
socket.emit('rider:subscribe', {
  driverId: 'driver-uuid',
  tripId: 'trip-uuid'
});
```

**Unsubscribe**:
```javascript
socket.emit('rider:unsubscribe', {
  driverId: 'driver-uuid'
});
```

### Server Events (Broadcast)

**Driver Location Update**:
```javascript
socket.on('driver:location', (data) => {
  console.log(data);
  // {
  //   driverId: 'uuid',
  //   latitude: 6.5244,
  //   longitude: 3.3792,
  //   bearing: 45.5,
  //   speed: 25.3,
  //   timestamp: '2024-12-15T10:30:45Z'
  // }
});
```

**Trip Status Update**:
```javascript
socket.on('trip:update', (data) => {
  console.log(data);
  // {
  //   tripId: 'uuid',
  //   status: 'EN_ROUTE',
  //   eta: '2024-12-15T11:00:00Z'
  // }
});
```

## REST API Endpoints

### Trip Management

**Update Trip Status**:
```bash
POST /v1/trips/{tripId}/status
Authorization: Bearer JWT_TOKEN

{
  "status": "EN_ROUTE",
  "location": {
    "latitude": 6.5244,
    "longitude": 3.3792
  }
}
```

**Get Trip Status**:
```bash
GET /v1/trips/{tripId}
Authorization: Bearer JWT_TOKEN
```

**Get Trip History**:
```bash
GET /v1/trips/{tripId}/history
Authorization: Bearer JWT_TOKEN
```

### Driver Status

**Get Driver Status**:
```bash
GET /v1/drivers/{driverId}/status
Authorization: Bearer JWT_TOKEN
```

**Get Driver Location History**:
```bash
GET /v1/drivers/{driverId}/locations?from=2024-12-15T00:00:00Z&to=2024-12-15T23:59:59Z
Authorization: Bearer JWT_TOKEN
```

### Health & Monitoring

**Health Check**:
```bash
GET /health

Response:
{
  "status": "healthy",
  "database": "connected",
  "redis": "connected",
  "websocket": "active",
  "connections": 1234
}
```

## Environment Variables

```bash
# Database
DATABASE_URL=postgresql+asyncpg://user:pass@localhost:5432/openride

# Redis
REDIS_URL=redis://localhost:6379/0
REDIS_PASSWORD=your_redis_password

# JWT
JWT_SECRET_KEY=your-secret-key
JWT_ALGORITHM=HS256

# Service
SERVICE_NAME=fleet-service
LOG_LEVEL=INFO
PORT=8096

# Rate Limiting
LOCATION_UPDATE_RATE_LIMIT=5  # seconds between updates
MAX_CONNECTIONS_PER_DRIVER=3
```

## Performance

- **Concurrent Connections**: 10,000+ per instance
- **Location Updates**: < 50ms latency
- **Horizontal Scaling**: Unlimited instances with Redis adapter
- **Database**: PostGIS spatial indexes for fast queries

## Security

- **JWT Authentication**: Required for WebSocket connection
- **Role-Based Access**: Drivers can only send locations, riders can only subscribe
- **Rate Limiting**: 1 location update per 5 seconds
- **Connection Limits**: Max 3 connections per driver
- **Session Tracking**: All connections logged with audit trail

## Testing

```bash
# Run all tests
pytest

# Run specific test
pytest tests/test_websocket.py -v

# Load testing
python tests/load_test.py --connections 1000
```

## Deployment

See [DEPLOYMENT.md](docs/DEPLOYMENT.md) for production deployment guide.

## Documentation

- [Architecture](docs/ARCHITECTURE.md) - System design and components
- [API Reference](docs/API_REFERENCE.md) - Complete API documentation
- [WebSocket Guide](docs/WEBSOCKET_GUIDE.md) - WebSocket integration guide
- [Deployment](docs/DEPLOYMENT.md) - Production deployment

## License

Proprietary - OpenRide Platform

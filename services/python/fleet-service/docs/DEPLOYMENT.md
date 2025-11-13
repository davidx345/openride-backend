# Fleet Service Deployment Guide

## Overview

This guide covers deploying the Fleet Service in various environments from development to production.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Development](#local-development)
3. [Docker Deployment](#docker-deployment)
4. [Kubernetes Deployment](#kubernetes-deployment)
5. [Environment Configuration](#environment-configuration)
6. [Database Migrations](#database-migrations)
7. [Monitoring & Observability](#monitoring--observability)
8. [Scaling](#scaling)
9. [Troubleshooting](#troubleshooting)

## Prerequisites

### Required Services

- **PostgreSQL 14+** with PostGIS 3.3+
- **Redis 7+** for pub/sub and session storage
- **Python 3.11+** runtime
- **Docker** (optional, for containerized deployment)
- **Kubernetes** (optional, for orchestration)

### System Requirements

**Minimum (Development):**
- CPU: 2 cores
- RAM: 4 GB
- Disk: 10 GB

**Recommended (Production):**
- CPU: 4+ cores
- RAM: 8+ GB
- Disk: 50+ GB (SSD recommended)
- Network: Low latency (<10ms to database/Redis)

## Local Development

### 1. Install Dependencies

```bash
cd services/python/fleet-service

# Create virtual environment
python3.11 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -e .
pip install -e ".[dev]"
```

### 2. Set Up Environment

```bash
# Copy example environment file
cp .env.example .env

# Edit .env with your configuration
# DATABASE_URL=postgresql+asyncpg://...
# REDIS_URL=redis://localhost:6379/0
# JWT_SECRET_KEY=your-secret-key
```

### 3. Run Database Migrations

```bash
# Run migrations
alembic upgrade head
```

### 4. Start Development Server

```bash
# Start with auto-reload
uvicorn app.main:app --reload --host 0.0.0.0 --port 8096
```

Access at:
- API: http://localhost:8096/docs
- WebSocket: ws://localhost:8096/socket.io
- Health: http://localhost:8096/health

## Docker Deployment

### 1. Build Image

```bash
cd services/python/fleet-service

# Build Docker image
docker build -t openride-fleet-service:latest .
```

### 2. Run with Docker Compose

```bash
cd ../../../  # Back to project root

# Start all services
docker-compose up -d fleet-service

# View logs
docker-compose logs -f fleet-service
```

### 3. Docker Compose Configuration

The fleet service is defined in `docker-compose.yml`:

```yaml
fleet-service:
  build:
    context: ./services/python/fleet-service
    dockerfile: Dockerfile
  container_name: openride-fleet-service
  environment:
    DATABASE_URL: postgresql+asyncpg://openride_user:openride_password@postgres:5432/openride
    REDIS_URL: redis://redis:6379/0
    REDIS_PASSWORD: openride_redis_password
    JWT_SECRET_KEY: ${JWT_SECRET_KEY}
    PORT: 8096
  ports:
    - "8096:8096"
    - "9096:9096"
  depends_on:
    - postgres
    - redis
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8096/health"]
    interval: 30s
    timeout: 10s
    retries: 5
```

## Kubernetes Deployment

### 1. Create ConfigMap

```yaml
# fleet-service-configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: fleet-service-config
  namespace: openride
data:
  SERVICE_NAME: "fleet-service"
  LOG_LEVEL: "INFO"
  PORT: "8096"
  SOCKETIO_PATH: "/socket.io"
  SOCKETIO_CORS_ALLOWED_ORIGINS: "*"
  LOCATION_UPDATE_RATE_LIMIT: "5"
  MAX_CONNECTIONS_PER_DRIVER: "3"
  WORKER_CONNECTIONS: "1000"
  DB_POOL_SIZE: "20"
  DB_MAX_OVERFLOW: "10"
  REDIS_MAX_CONNECTIONS: "50"
  ENABLE_METRICS: "true"
  METRICS_PORT: "9096"
```

### 2. Create Secret

```yaml
# fleet-service-secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: fleet-service-secret
  namespace: openride
type: Opaque
stringData:
  DATABASE_URL: "postgresql+asyncpg://user:password@postgres:5432/openride"
  REDIS_URL: "redis://redis:6379/0"
  REDIS_PASSWORD: "your-redis-password"
  JWT_SECRET_KEY: "your-jwt-secret-key"
  JWT_ALGORITHM: "HS256"
```

### 3. Create Deployment

```yaml
# fleet-service-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: fleet-service
  namespace: openride
spec:
  replicas: 3
  selector:
    matchLabels:
      app: fleet-service
  template:
    metadata:
      labels:
        app: fleet-service
    spec:
      containers:
      - name: fleet-service
        image: openride-fleet-service:latest
        ports:
        - containerPort: 8096
          name: http
        - containerPort: 9096
          name: metrics
        envFrom:
        - configMapRef:
            name: fleet-service-config
        - secretRef:
            name: fleet-service-secret
        livenessProbe:
          httpGet:
            path: /live
            port: 8096
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /ready
            port: 8096
          initialDelaySeconds: 10
          periodSeconds: 5
        resources:
          requests:
            cpu: "500m"
            memory: "512Mi"
          limits:
            cpu: "2000m"
            memory: "2Gi"
```

### 4. Create Service

```yaml
# fleet-service-service.yaml
apiVersion: v1
kind: Service
metadata:
  name: fleet-service
  namespace: openride
spec:
  type: ClusterIP
  ports:
  - port: 8096
    targetPort: 8096
    protocol: TCP
    name: http
  - port: 9096
    targetPort: 9096
    protocol: TCP
    name: metrics
  selector:
    app: fleet-service
```

### 5. Deploy to Kubernetes

```bash
# Create namespace
kubectl create namespace openride

# Apply configurations
kubectl apply -f fleet-service-configmap.yaml
kubectl apply -f fleet-service-secret.yaml
kubectl apply -f fleet-service-deployment.yaml
kubectl apply -f fleet-service-service.yaml

# Check status
kubectl get pods -n openride -l app=fleet-service
kubectl logs -n openride -l app=fleet-service --tail=100 -f
```

## Environment Configuration

### Required Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `DATABASE_URL` | PostgreSQL connection string | - | Yes |
| `REDIS_URL` | Redis connection URL | - | Yes |
| `JWT_SECRET_KEY` | Secret key for JWT signing | - | Yes |

### Optional Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVICE_NAME` | Service name for logging | `fleet-service` |
| `LOG_LEVEL` | Logging level | `INFO` |
| `PORT` | HTTP server port | `8096` |
| `SOCKETIO_PATH` | Socket.IO endpoint path | `/socket.io` |
| `SOCKETIO_CORS_ALLOWED_ORIGINS` | CORS origins | `*` |
| `LOCATION_UPDATE_RATE_LIMIT` | Seconds between location updates | `5` |
| `MAX_CONNECTIONS_PER_DRIVER` | Max concurrent connections | `3` |
| `WORKER_CONNECTIONS` | Uvicorn worker connections | `1000` |
| `DB_POOL_SIZE` | Database connection pool size | `20` |
| `DB_MAX_OVERFLOW` | Max overflow connections | `10` |
| `REDIS_MAX_CONNECTIONS` | Max Redis connections | `50` |
| `ENABLE_METRICS` | Enable Prometheus metrics | `true` |
| `METRICS_PORT` | Prometheus metrics port | `9096` |

## Database Migrations

### Run Migrations

```bash
# Upgrade to latest
alembic upgrade head

# Upgrade to specific version
alembic upgrade <revision>

# Downgrade one version
alembic downgrade -1

# Show current version
alembic current

# Show migration history
alembic history
```

### Create New Migration

```bash
# Auto-generate migration
alembic revision --autogenerate -m "description"

# Create empty migration
alembic revision -m "description"
```

## Monitoring & Observability

### Health Checks

```bash
# Liveness probe (Kubernetes)
curl http://localhost:8096/live

# Readiness probe (Kubernetes)
curl http://localhost:8096/ready

# Full health check
curl http://localhost:8096/health
```

### Prometheus Metrics

Metrics exposed at: `http://localhost:9096/metrics`

**Key Metrics:**
- `http_requests_total` - Total HTTP requests
- `http_request_duration_seconds` - Request duration
- `websocket_connections_total` - Active WebSocket connections
- `location_updates_total` - Total location updates
- `trip_status_changes_total` - Total trip status changes
- `database_pool_size` - Database connection pool size
- `redis_commands_total` - Total Redis commands

### Logging

Logs are written to stdout in JSON format:

```json
{
  "event": "Location updated",
  "level": "info",
  "logger": "app.services.location",
  "timestamp": "2024-12-15T10:30:00.000Z",
  "driver_id": "...",
  "latitude": 37.7749,
  "longitude": -122.4194
}
```

## Scaling

### Horizontal Scaling

Fleet Service supports horizontal scaling with Redis pub/sub:

```yaml
# Scale to 5 replicas
kubectl scale deployment fleet-service --replicas=5 -n openride
```

**Architecture:**
```
Load Balancer
    ├── Fleet Service Instance 1
    ├── Fleet Service Instance 2
    ├── Fleet Service Instance 3
    └── Fleet Service Instance 4
         ↓
    Redis Pub/Sub (broadcast messages across instances)
         ↓
    PostgreSQL (shared state)
```

### Performance Tuning

**Database Connection Pool:**
```env
DB_POOL_SIZE=20
DB_MAX_OVERFLOW=10
```

**Redis Connections:**
```env
REDIS_MAX_CONNECTIONS=50
```

**Uvicorn Workers:**
```bash
uvicorn app.main:app --workers 4 --host 0.0.0.0 --port 8096
```

### Capacity Planning

**Per Instance:**
- CPU: 2 cores
- RAM: 2 GB
- Concurrent Connections: ~10,000

**For 50,000 concurrent connections:**
- Instances: 5-6
- Total CPU: 10-12 cores
- Total RAM: 10-12 GB

## Troubleshooting

### Common Issues

#### 1. WebSocket Connection Failed

**Symptoms:**
```
WebSocket connection failed: 401 Unauthorized
```

**Solution:**
- Verify JWT token is valid and not expired
- Check `JWT_SECRET_KEY` matches across services
- Ensure token is sent in auth handshake

#### 2. Database Connection Failed

**Symptoms:**
```
Database connection error: could not connect to server
```

**Solution:**
```bash
# Check DATABASE_URL format
DATABASE_URL=postgresql+asyncpg://user:password@host:5432/dbname

# Test connection
psql -h localhost -U openride_user -d openride

# Check PostGIS extension
psql -h localhost -U openride_user -d openride -c "SELECT PostGIS_Version();"
```

#### 3. Redis Connection Failed

**Symptoms:**
```
Redis connection error: Connection refused
```

**Solution:**
```bash
# Test Redis connection
redis-cli -h localhost -p 6379 ping

# With password
redis-cli -h localhost -p 6379 -a your-password ping

# Check REDIS_URL format
REDIS_URL=redis://redis:6379/0
REDIS_PASSWORD=your-password
```

#### 4. High Memory Usage

**Symptoms:**
- Memory usage continuously increasing
- OOM kills in Kubernetes

**Solution:**
```env
# Reduce connection pool sizes
DB_POOL_SIZE=10
REDIS_MAX_CONNECTIONS=25
WORKER_CONNECTIONS=500

# Increase resources in Kubernetes
resources:
  limits:
    memory: "4Gi"
```

#### 5. Location Updates Not Broadcasting

**Symptoms:**
- Riders not receiving location updates
- WebSocket events not delivered

**Solution:**
```bash
# Check Redis pub/sub
redis-cli -a your-password subscribe fleet:location:updates

# Verify Socket.IO Redis adapter
# Check logs for: "WebSocket services initialized"

# Test WebSocket connection
wscat -c ws://localhost:8096/socket.io -H "Authorization: Bearer <token>"
```

### Debug Mode

Enable debug logging:

```env
LOG_LEVEL=DEBUG
```

View detailed logs:

```bash
# Docker
docker-compose logs -f fleet-service

# Kubernetes
kubectl logs -n openride -l app=fleet-service --tail=100 -f
```

### Support

For additional support:
- GitHub Issues: https://github.com/openride/backend/issues
- Documentation: https://docs.openride.com
- Slack: #fleet-service

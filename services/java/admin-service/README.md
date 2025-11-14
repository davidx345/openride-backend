# OpenRide Admin Service

Centralized admin service for OpenRide platform management and operations.

## Features

### 1. Dispute Management
- Create and track disputes/support tickets
- Assign disputes to admins for investigation
- Resolve or reject disputes with detailed notes
- View disputes by booking, status, or type
- Dispute statistics and reporting

### 2. User Suspension/Ban Management
- Temporary suspensions with end dates
- Permanent bans
- Automatic expiration of temporary suspensions
- Suspension status checking
- Suspension history tracking

### 3. Audit Logging
- Comprehensive audit trail for all admin actions
- Filter by entity, actor, action type, or date range
- Recent admin actions view
- Automatic log cleanup (365-day retention)

### 4. System Health & Metrics
- Real-time system health dashboard
- Booking metrics and statistics
- Payment metrics and success rates
- User growth analytics
- Service health monitoring
- Materialized views for performance

### 5. Cross-Service Integration
- Integrates with booking-service for booking management
- Integrates with payment-service for refunds
- Integrates with user-service for KYC and suspensions
- Integrates with notification-service for alerts

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Language**: Java 17
- **Database**: PostgreSQL 14+ (shared with other services)
- **Cache**: Redis 7+
- **Security**: Spring Security + JWT
- **API Documentation**: OpenAPI 3 / Swagger
- **Build Tool**: Maven

## Prerequisites

- Java 17 or higher
- Maven 3.8+
- PostgreSQL 14+ with database `openride`
- Redis 7+
- All Phase 11 migrations applied

## Environment Variables

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=openride
DB_USER=openride_user
DB_PASSWORD=openride_pass

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT
JWT_SECRET=your-256-bit-secret-key-change-in-production-min-32-chars

# Server
SERVER_PORT=8086

# Service URLs (for inter-service communication)
BOOKING_SERVICE_URL=http://localhost:8081
PAYMENT_SERVICE_URL=http://localhost:8082
USER_SERVICE_URL=http://localhost:8083
NOTIFICATION_SERVICE_URL=http://localhost:8089
```

## Running Locally

### 1. Build the service
```bash
cd services/java/admin-service
mvn clean package -DskipTests
```

### 2. Run migrations (if not already run)
```bash
# From project root
psql -U openride_user -d openride -f infrastructure/docker/migrations/V11_001__create_disputes_table.sql
psql -U openride_user -d openride -f infrastructure/docker/migrations/V11_002__create_user_suspensions_table.sql
psql -U openride_user -d openride -f infrastructure/docker/migrations/V11_003__enhance_audit_logs.sql
psql -U openride_user -d openride -f infrastructure/docker/migrations/V11_004__create_admin_metrics_views.sql
```

### 3. Run the service
```bash
java -jar target/admin-service-1.0.0-SNAPSHOT.jar
```

Or using Maven:
```bash
mvn spring-boot:run
```

## API Endpoints

### Disputes
- `POST /v1/admin/disputes` - Create dispute
- `GET /v1/admin/disputes` - Get disputes (with filters)
- `GET /v1/admin/disputes/{id}` - Get dispute details
- `PATCH /v1/admin/disputes/{id}/resolve` - Resolve dispute
- `PATCH /v1/admin/disputes/{id}/assign` - Assign dispute
- `GET /v1/admin/disputes/booking/{bookingId}` - Get disputes by booking
- `GET /v1/admin/disputes/statistics` - Get statistics

### Suspensions
- `POST /v1/admin/suspensions` - Suspend user
- `DELETE /v1/admin/suspensions/{id}` - Lift suspension
- `GET /v1/admin/suspensions/check/{userId}` - Check status
- `GET /v1/admin/suspensions/user/{userId}` - Get user suspensions
- `GET /v1/admin/suspensions/active` - Get all active suspensions

### Audit Logs
- `GET /v1/admin/audit-logs/entity` - Get logs by entity
- `GET /v1/admin/audit-logs/actor/{actorId}` - Get logs by actor
- `GET /v1/admin/audit-logs/action` - Get logs by action
- `GET /v1/admin/audit-logs/date-range` - Get logs by date range
- `GET /v1/admin/audit-logs/recent` - Get recent admin actions

### Metrics
- `GET /v1/admin/metrics/realtime` - Get real-time system health
- `POST /v1/admin/metrics/refresh` - Refresh metrics manually
- `GET /v1/admin/metrics/bookings` - Get booking metrics
- `GET /v1/admin/metrics/payments` - Get payment metrics
- `GET /v1/admin/metrics/user-growth` - Get user growth metrics

## API Documentation

Swagger UI available at:
```
http://localhost:8086/admin-service/swagger-ui.html
```

OpenAPI JSON:
```
http://localhost:8086/admin-service/v3/api-docs
```

## Authentication

All endpoints require JWT authentication with `ADMIN` role.

Example:
```bash
curl -H "Authorization: Bearer <JWT_TOKEN>" \
     http://localhost:8086/admin-service/v1/admin/metrics/realtime
```

## Scheduled Jobs

### 1. Deactivate Expired Suspensions
- **Frequency**: Every hour
- **Purpose**: Automatically deactivates temporary suspensions past their end date
- **Cron**: `0 0 * * * *`

### 2. Cleanup Old Audit Logs
- **Frequency**: Daily at 2 AM
- **Purpose**: Removes audit logs older than 365 days
- **Cron**: `0 0 2 * * *`

### 3. Refresh Metrics (TODO)
- **Frequency**: Every 15 minutes
- **Purpose**: Refreshes materialized views for dashboard metrics

## Database Tables

### disputes
Stores booking disputes and support tickets.

### user_suspensions
Stores user suspension/ban records.

### audit_logs (enhanced)
Comprehensive audit trail with full context.

### Materialized Views
- `mv_daily_booking_metrics` - Daily booking aggregations
- `mv_daily_payment_metrics` - Daily payment aggregations
- `mv_user_growth_metrics` - Daily user growth
- `mv_system_health` - Real-time health snapshot

## Caching Strategy

- **System Metrics**: Cached for 5 minutes in Redis
- **Cache Key**: `systemMetrics`
- **Eviction**: TTL-based

## Error Handling

All errors return consistent JSON format:
```json
{
  "timestamp": "2024-11-14T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed error message",
  "details": {
    "field": "error description"
  }
}
```

## Testing

Run unit tests:
```bash
mvn test
```

Run integration tests:
```bash
mvn verify
```

## Docker

Build image:
```bash
docker build -t openride/admin-service:latest .
```

Run container:
```bash
docker run -p 8086:8086 \
  -e DB_HOST=host.docker.internal \
  -e REDIS_HOST=host.docker.internal \
  openride/admin-service:latest
```

## Phase 11 Completion Checklist

- [x] Database migrations created
- [x] Dispute management system implemented
- [x] User suspension/ban functionality implemented
- [x] Audit log viewer with filtering implemented
- [x] System health metrics dashboard implemented
- [x] Admin booking search (in booking-service)
- [ ] Manual refund processing (extend payments-service)
- [ ] Driver verification queue (extend user-service)
- [x] OpenAPI documentation
- [ ] Comprehensive tests
- [ ] README and runbook

## Next Steps

1. Extend payments-service with admin refund endpoint
2. Extend user-service with pending driver verification endpoint
3. Write comprehensive tests (unit + integration)
4. Performance testing with metrics queries
5. Security audit
6. Production deployment configuration

## Support

For issues or questions, contact the OpenRide Platform Team.

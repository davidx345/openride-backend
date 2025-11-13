# Booking Service

**Version:** 1.0.0  
**Port:** 8083  
**Context Path:** `/api`

## Overview

The Booking Service is a critical component of the OpenRide platform, responsible for managing the entire booking lifecycle, seat inventory, and payment integration. It implements a robust state machine, distributed locking for concurrency safety, and event-driven architecture for downstream service integration.

### Key Features

- **🔒 Distributed Locking**: Redisson-based locks prevent race conditions on seat booking
- **🎰 State Machine**: 10-state booking lifecycle with validated transitions
- **💺 Seat Inventory**: Multi-source reconciliation (PostgreSQL + Redis)
- **⏱️ Seat Holds**: Redis-based temporary holds with configurable TTL
- **💳 Payment Integration**: Seamless payment service integration
- **♻️ Refund Policies**: Automated refund calculation based on cancellation time
- **📡 Event Publishing**: Kafka events for booking lifecycle changes
- **🔐 Security**: JWT authentication and authorization
- **📊 Monitoring**: Prometheus metrics and health checks
- **📝 API Documentation**: OpenAPI/Swagger UI

## Architecture

### State Machine

```
PENDING → HELD → PAYMENT_INITIATED → PAID → CONFIRMED → CHECKED_IN → COMPLETED
                                                           ↓
                                                      CANCELLED
                                                           ↓
                                                       EXPIRED
                                                           ↓
                                                        FAILED
```

#### State Transitions

| From State | Valid Next States | Trigger |
|-----------|-------------------|---------|
| PENDING | HELD, EXPIRED, FAILED | Seat hold success/failure/timeout |
| HELD | PAYMENT_INITIATED, EXPIRED, CANCELLED | Payment initiation/expiry/cancel |
| PAYMENT_INITIATED | PAID, FAILED, CANCELLED | Payment result |
| PAID | CONFIRMED, FAILED | Confirmation process |
| CONFIRMED | CHECKED_IN, CANCELLED | Check-in/cancellation |
| CHECKED_IN | COMPLETED, CANCELLED | Trip completion |

### Distributed Locking Strategy

- **Route-Level Locks**: `lock:route:{routeId}:{date}` for seat operations
- **Booking-Level Locks**: `lock:booking:{bookingId}` for booking updates
- **Configuration**:
  - Wait time: 5 seconds
  - Lease time: 10 seconds
  - Automatic release on completion/failure

### Seat Inventory Management

**Multi-Source Reconciliation:**

1. **Total Seats**: From route configuration
2. **Confirmed Bookings**: PostgreSQL (CONFIRMED, CHECKED_IN statuses)
3. **Redis Holds**: Temporary holds with 10-minute TTL
4. **Available Seats**: Total - Confirmed - Held

**Redis Keys:**
- Seat holds: `seat:hold:{routeId}:{date}:{seatNumber}`
- Booking holds: `booking:hold:{bookingId}`

### Refund Policy

| Time Before Departure | Refund Percentage |
|-----------------------|-------------------|
| > 24 hours | 100% |
| 6 - 24 hours | 50% |
| < 6 hours | 0% |

*Configurable via `application.yml`*

## Getting Started

### Prerequisites

- **Java**: 21+
- **Maven**: 3.9+
- **PostgreSQL**: 14+
- **Redis**: 7+
- **Kafka**: 3.6+

### Local Development

1. **Start Infrastructure**:
   ```bash
   cd infrastructure
   docker-compose up -d postgres redis kafka
   ```

2. **Set Environment Variables**:
   ```bash
   export DB_HOST=localhost
   export DB_PORT=5432
   export DB_NAME=openride_booking
   export DB_USER=postgres
   export DB_PASSWORD=postgres
   export REDIS_HOST=localhost
   export REDIS_PORT=6379
   export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
   export JWT_SECRET=your-secret-key-change-in-production
   ```

3. **Build & Run**:
   ```bash
   cd services/java/booking-service
   mvn clean install
   mvn spring-boot:run
   ```

4. **Access Swagger UI**:
   ```
   http://localhost:8083/api/swagger-ui.html
   ```

### Docker

```bash
docker build -t openride/booking-service:latest .
docker run -p 8083:8083 \
  -e DB_HOST=postgres \
  -e REDIS_HOST=redis \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  openride/booking-service:latest
```

## API Endpoints

### Create Booking

```http
POST /api/v1/bookings
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "routeId": "uuid",
  "originStopId": "string",
  "destinationStopId": "string",
  "travelDate": "2024-01-15",
  "seatsBooked": 2,
  "idempotencyKey": "optional-unique-key"
}
```

**Response:**
```json
{
  "booking": {
    "id": "uuid",
    "bookingReference": "BK20240115ABC123",
    "status": "HELD",
    "riderId": "uuid",
    "routeId": "uuid",
    "travelDate": "2024-01-15",
    "seatsBooked": 2,
    "seatNumbers": [1, 2],
    "totalPrice": 50.00,
    "platformFee": 2.50,
    "expiresAt": "2024-01-15T10:10:00Z"
  },
  "expiresAt": "2024-01-15T10:10:00Z",
  "message": "Booking created successfully. Please complete payment."
}
```

### Get Booking by ID

```http
GET /api/v1/bookings/{id}
Authorization: Bearer {jwt_token}
```

### Get Booking by Reference

```http
GET /api/v1/bookings/reference/{reference}
Authorization: Bearer {jwt_token}
```

### List My Bookings

```http
GET /api/v1/bookings?page=0&size=20
Authorization: Bearer {jwt_token}
```

### Get Upcoming Trips

```http
GET /api/v1/bookings/upcoming
Authorization: Bearer {jwt_token}
```

### Cancel Booking

```http
POST /api/v1/bookings/{id}/cancel
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "reason": "Changed travel plans"
}
```

### Confirm Booking (Internal)

```http
POST /api/v1/bookings/{id}/confirm
Authorization: Bearer {jwt_token}
Content-Type: application/json

{
  "paymentId": "uuid"
}
```

## Configuration

### Application Properties

```yaml
# Booking Configuration
booking:
  hold:
    ttl-minutes: 10              # Seat hold duration
    extension-minutes: 15        # Extension for payment
  cancellation:
    full-refund-hours: 24        # Full refund threshold
    partial-refund-hours: 6      # Partial refund threshold
    partial-refund-percentage: 0.50  # 50% refund
  platform-fee-percentage: 0.05  # 5% platform fee
  max-seats-per-booking: 4       # Maximum seats per booking
  lock:
    wait-time-seconds: 5         # Lock wait time
    lease-time-seconds: 10       # Lock lease time

# Scheduler
scheduler:
  cleanup:
    expired-holds-cron: "0 */5 * * * *"    # Every 5 minutes
    orphaned-redis-cron: "0 */15 * * * *"  # Every 15 minutes
  enabled: true

# External Services
services:
  driver-service:
    base-url: http://localhost:8082/api
    timeout-ms: 3000
  payment-service:
    base-url: http://localhost:8086/api
    timeout-ms: 10000
  notification-service:
    base-url: http://localhost:8087/api
    timeout-ms: 3000
```

## Events

### Kafka Topics

| Topic | Event | Trigger |
|-------|-------|---------|
| `booking.created` | BookingCreatedEvent | Booking created and seats held |
| `booking.confirmed` | BookingConfirmedEvent | Payment confirmed |
| `booking.cancelled` | BookingCancelledEvent | Booking cancelled |
| `booking.completed` | BookingCompletedEvent | Trip completed |

### Event Schemas

**BookingCreatedEvent:**
```json
{
  "bookingId": "uuid",
  "bookingReference": "BK20240115ABC123",
  "riderId": "uuid",
  "routeId": "uuid",
  "travelDate": "2024-01-15",
  "numberOfSeats": 2,
  "seatNumbers": ["1", "2"],
  "totalAmount": 50.00,
  "expiresAt": "2024-01-15T10:10:00Z",
  "eventId": "uuid",
  "eventTimestamp": "2024-01-15T10:00:00Z"
}
```

## Database Schema

### Tables

#### bookings
- Primary key: `id` (UUID)
- Unique constraints: `booking_reference`, `idempotency_key`
- Indexes: rider_id, route_id, status, travel_date, composite indexes
- Triggers: Auto-update timestamps, audit trail

#### booking_status_history
- Audit trail for status transitions
- Stores `from_status`, `to_status`, `reason`, `metadata` (JSONB)

#### seat_holds
- Redis backup for reconciliation
- Stores seat holds with expiry

## Monitoring

### Health Checks

```http
GET /api/actuator/health
```

### Metrics

```http
GET /api/actuator/metrics
GET /api/actuator/prometheus
```

### Key Metrics

- `booking_creation_total`: Total bookings created
- `booking_confirmation_total`: Total confirmations
- `booking_cancellation_total`: Total cancellations
- `seat_hold_duration`: Seat hold time distribution
- `distributed_lock_wait_time`: Lock wait time

## Error Handling

### Error Codes

| Code | Message | HTTP Status |
|------|---------|-------------|
| BOOKING_NOT_FOUND | Booking not found | 404 |
| INSUFFICIENT_SEATS | Not enough seats available | 400 |
| INVALID_STATE_TRANSITION | Invalid status transition | 400 |
| BOOKING_NOT_CANCELLABLE | Cannot cancel booking | 400 |
| SEAT_HOLD_FAILED | Failed to hold seats | 500 |
| LOCK_ACQUISITION_FAILED | Failed to acquire lock | 503 |

## Testing

### Run Unit Tests

```bash
mvn test
```

### Run Integration Tests

```bash
mvn verify -P integration-tests
```

### Run with Coverage

```bash
mvn clean verify jacoco:report
```

Coverage report: `target/site/jacoco/index.html`

## Security

- **Authentication**: JWT Bearer tokens
- **Authorization**: Rider can only access their own bookings
- **Input Validation**: Bean Validation annotations
- **Rate Limiting**: (Planned - Todo #13)

## Performance

### Targets

- P50 latency: < 100ms
- P95 latency: < 200ms
- P99 latency: < 500ms
- Throughput: > 1000 bookings/second

### Optimization Strategies

- Connection pooling (Hikari)
- Redis connection pooling
- Distributed locking minimizes lock scope
- Pessimistic locking only for updates
- Indexed database queries
- Async event publishing

## Troubleshooting

### Common Issues

**1. Seat Hold Failures**

- Check Redis connectivity
- Verify Redis TTL settings
- Check distributed lock timeouts

**2. Lock Acquisition Timeouts**

- Increase `lock.wait-time-seconds`
- Check Redisson configuration
- Verify Redis performance

**3. Event Publishing Failures**

- Check Kafka connectivity
- Verify topic configuration
- Check Kafka producer settings

## Development

### Code Structure

```
src/main/java/com/openride/booking/
├── config/              # Configuration classes
├── controller/          # REST controllers
├── service/             # Business logic
├── repository/          # JPA repositories
├── model/               # JPA entities
├── dto/                 # Request/Response DTOs
├── mapper/              # Entity-DTO mappers
├── event/               # Kafka event DTOs
├── exception/           # Custom exceptions
├── scheduler/           # Scheduled jobs
└── client/              # Service clients
```

### Build Plugins

- Spring Boot Maven Plugin
- Flyway Maven Plugin
- JaCoCo (code coverage)
- Maven Compiler Plugin (Java 21)

## Contributing

See root DEVELOPMENT.md for contribution guidelines.

## License

Proprietary - OpenRide Platform

## Support

For issues and questions, contact: backend-team@openride.com

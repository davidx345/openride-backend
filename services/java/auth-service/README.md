# Auth Service

Authentication and authorization service for the OpenRide platform.

## Features

- Phone-based OTP authentication
- JWT token generation and validation
- Refresh token mechanism
- Rate limiting (Redis-based)
- SMS integration via Twilio
- Distributed tracing with correlation IDs

## API Endpoints

### POST /api/v1/auth/send-otp
Send OTP to phone number.

**Request:**
```json
{
  "phone": "+2348012345678"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "message": "OTP sent successfully",
    "expiresIn": 300
  }
}
```

### POST /api/v1/auth/verify-otp
Verify OTP and get tokens.

**Request:**
```json
{
  "phone": "+2348012345678",
  "code": "123456"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGc...",
    "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
    "user": {
      "id": "uuid",
      "phone": "+2348012345678",
      "role": "RIDER",
      "fullName": "John Doe",
      "email": "john@example.com"
    }
  }
}
```

### POST /api/v1/auth/refresh-token
Refresh access token.

**Request:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGc..."
  }
}
```

### POST /api/v1/auth/logout
Invalidate refresh token.

**Request:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

## Environment Variables

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=openride
DB_USER=openride_user
DB_PASSWORD=openride_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=openride_redis_password

# JWT
JWT_SECRET_KEY=your-secret-key-min-256-bits

# Twilio
TWILIO_ACCOUNT_SID=your-account-sid
TWILIO_AUTH_TOKEN=your-auth-token
TWILIO_PHONE_NUMBER=+1234567890

# User Service
USER_SERVICE_URL=http://localhost:8082/api

# Logging
LOG_LEVEL=DEBUG
SQL_LOG_LEVEL=WARN
```

## Running Locally

### Prerequisites
- Java 17
- Maven 3.9+
- PostgreSQL 14+
- Redis 7+

### Steps

1. **Install shared commons library:**
```bash
cd ../../shared/java-commons
mvn clean install
```

2. **Run database migrations:**
```bash
cd ../../services/java/auth-service
mvn flyway:migrate
```

3. **Build and run:**
```bash
mvn clean install
mvn spring-boot:run
```

4. **Access Swagger UI:**
```
http://localhost:8081/api/swagger-ui.html
```

## Running with Docker

```bash
docker build -t openride/auth-service:latest .
docker run -p 8081:8081 \
  -e DB_HOST=postgres \
  -e REDIS_HOST=redis \
  -e JWT_SECRET_KEY=your-secret \
  openride/auth-service:latest
```

## Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

## Rate Limits

- **OTP Send**: 3 requests per phone per hour
- **OTP Verify**: 10 attempts per phone per hour
- **OTP Attempts per Code**: 5 attempts max before expiry

## Security

- OTP expires after 5 minutes
- Access tokens expire after 1 hour
- Refresh tokens expire after 7 days
- All endpoints use HTTPS in production
- CORS configured for frontend origins only
- Input validation on all endpoints
- SQL injection protection via JPA
- XSS protection via Spring Security

## Monitoring

- Actuator endpoints: `/actuator/health`, `/actuator/metrics`
- Prometheus metrics: `/actuator/prometheus`
- Correlation ID in all logs for distributed tracing

## Architecture

```
┌─────────────┐      ┌──────────┐      ┌───────────┐
│  Controller │─────▶│ Service  │─────▶│Repository │
└─────────────┘      └──────────┘      └───────────┘
                           │                  │
                           ▼                  ▼
                     ┌──────────┐      ┌───────────┐
                     │  Redis   │      │PostgreSQL │
                     │(Rate Lim)│      │  (OTPs)   │
                     └──────────┘      └───────────┘
                           │
                           ▼
                     ┌──────────┐
                     │  Twilio  │
                     │  (SMS)   │
                     └──────────┘
```

## License

Proprietary - OpenRide Platform

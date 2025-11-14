# OpenRide Notification Service

A comprehensive microservice for managing notifications across multiple channels (Push, SMS, Email) in the OpenRide platform.

## Features

- **Multi-Channel Notifications**
  - Push Notifications (Firebase Cloud Messaging)
  - SMS Notifications (Termii API for Nigeria)
  - Email Notifications (SendGrid)

- **JWT Authentication** ✨ NEW
  - Secure endpoint access with Bearer tokens
  - Role-based access control (user/admin)
  - Token expiration and validation
  - HTTPBearer security scheme

- **Template Management**
  - Jinja2-based template rendering
  - Dynamic content substitution
  - Database-stored templates
  - Admin-only template CRUD operations

- **User Preferences**
  - Per-user notification settings
  - Channel-specific enable/disable
  - Notification type filtering
  - Quiet hours support

- **Async Processing**
  - Celery-based task queue
  - Background notification delivery
  - Automatic retry logic
  - Scheduled maintenance tasks

- **Delivery Tracking**
  - Complete notification history
  - Status tracking (pending, sent, delivered, failed)
  - Error logging
  - Retry count tracking

- **Comprehensive Testing** ✨ NEW
  - 128+ tests covering all features
  - >90% code coverage
  - Unit, integration, and E2E tests
  - Mock external services

## Technology Stack

- **Python 3.11+**
- **FastAPI** - Async web framework
- **SQLAlchemy 2.0** - Async ORM
- **PostgreSQL 14+** - Primary database
- **Redis 7+** - Celery broker & caching
- **Celery 5.3+** - Async task processing
- **Firebase Admin SDK** - Push notifications
- **Termii API** - SMS delivery
- **SendGrid** - Email delivery
- **Alembic** - Database migrations

## Setup

### Prerequisites

- Python 3.11+
- PostgreSQL 14+
- Redis 7+
- Firebase Admin credentials (JSON file)
- Termii API key
- SendGrid API key

### Installation

1. **Clone the repository**
```bash
cd services/python/notification-service
```

2. **Install dependencies**
```bash
poetry install
```

3. **Configure environment**
```bash
cp .env.example .env
# Edit .env with your configuration
```

4. **Run database migrations**
```bash
alembic upgrade head
```

5. **Start the service**
```bash
# Development
uvicorn app.main:app --reload --port 8095

# Production
uvicorn app.main:app --host 0.0.0.0 --port 8095 --workers 4
```

6. **Start Celery worker**
```bash
# In a separate terminal
celery -A celery_app worker --loglevel=info -Q notifications,broadcasts,retries,maintenance
```

7. **Start Celery Beat (scheduler)**
```bash
# In another terminal
celery -A celery_app beat --loglevel=info
```

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL connection URL | `postgresql+asyncpg://...` |
| `REDIS_URL` | Redis connection URL | `redis://localhost:6379/3` |
| `CELERY_BROKER_URL` | Celery broker URL | `redis://localhost:6379/4` |
| `JWT_SECRET_KEY` | JWT signing secret key | `your-secret-key-change-in-production` |
| `JWT_ALGORITHM` | JWT algorithm | `HS256` |
| `JWT_EXPIRATION_MINUTES` | Token expiration time | `60` |
| `FCM_CREDENTIALS_PATH` | Firebase credentials file | `./config/firebase-credentials.json` |
| `TERMII_API_KEY` | Termii API key | - |
| `TERMII_SENDER_ID` | Termii sender ID | `OpenRide` |
| `SENDGRID_API_KEY` | SendGrid API key | - |
| `SENDGRID_FROM_EMAIL` | SendGrid from email | `noreply@openride.com` |

See `.env.example` for complete configuration options.

## Authentication

All API endpoints (except health checks) require JWT authentication.

### Using Authentication

```bash
# Include Bearer token in Authorization header
curl -H "Authorization: Bearer <your-jwt-token>" \
  http://localhost:8095/v1/notifications/history
```

### Token Structure

```json
{
  "sub": "user-uuid",
  "iat": 1234567890,
  "exp": 1234571490,
  "roles": ["user"]
}
```

### Admin Endpoints

Admin template management endpoints require the `admin` role in the JWT token:
- `POST /v1/admin/notification-templates`
- `GET /v1/admin/notification-templates`
- `PUT /v1/admin/notification-templates/{id}`
- `DELETE /v1/admin/notification-templates/{id}`

## API Endpoints

### Notifications

- `POST /v1/notifications/send` - Send notification (sync) 🔒 Auth required
- `POST /v1/notifications/send-async` - Send notification (async via Celery)
- `POST /v1/notifications/broadcast` - Broadcast to multiple users
- `GET /v1/notifications/history` - Get user notification history 🔒 Auth required
- `GET /v1/notifications/{id}` - Get notification details 🔒 Auth required

### Device Tokens

- `POST /v1/notifications/tokens` - Register FCM token 🔒 Auth required
- `GET /v1/notifications/tokens` - Get user's tokens 🔒 Auth required
- `DELETE /v1/notifications/tokens/{id}` - Delete token 🔒 Auth required

### Preferences

- `GET /v1/notifications/preferences` - Get user preferences 🔒 Auth required
- `PATCH /v1/notifications/preferences` - Update preferences 🔒 Auth required

### Admin Templates

- `POST /v1/admin/notification-templates` - Create template 🔒 Admin only
- `GET /v1/admin/notification-templates` - List templates 🔒 Admin only
- `GET /v1/admin/notification-templates/{id}` - Get template 🔒 Admin only
- `PUT /v1/admin/notification-templates/{id}` - Update template 🔒 Admin only
- `DELETE /v1/admin/notification-templates/{id}` - Delete template 🔒 Admin only

### Health

- `GET /health` - Basic health check
- `GET /health/ready` - Readiness probe

## Usage Examples

### Send Push Notification

```python
import httpx

async with httpx.AsyncClient() as client:
    response = await client.post(
        "http://localhost:8095/v1/notifications/send",
        json={
            "user_id": "123e4567-e89b-12d3-a456-426614174000",
            "notification_type": "booking_confirmed",
            "channels": ["push"],
            "template_data": {
                "route_name": "Lagos - Abuja",
                "trip_date": "2025-11-15",
                "booking_id": "BK001",
                "amount": "5000"
            }
        }
    )
    print(response.json())
```

### Send SMS Notification

```python
response = await client.post(
    "http://localhost:8095/v1/notifications/send-async",
    json={
        "user_id": "123e4567-e89b-12d3-a456-426614174000",
        "notification_type": "payment_success",
        "channels": ["sms"],
        "phone_number": "+2348012345678",
        "template_data": {
            "amount": "5000",
            "reference": "PAY12345"
        }
    }
)
```

### Broadcast Notification

```python
response = await client.post(
    "http://localhost:8095/v1/notifications/broadcast",
    json={
        "user_ids": [
            "123e4567-e89b-12d3-a456-426614174000",
            "223e4567-e89b-12d3-a456-426614174001"
        ],
        "notification_type": "route_cancelled",
        "channels": ["push", "sms"],
        "template_data": {
            "route_name": "Lagos - Ibadan",
            "trip_date": "2025-11-15"
        }
    }
)
```

## Docker Deployment

### Build Image

```bash
docker build -t openride-notification-service:latest .
```

### Run Container

```bash
docker run -d \
  --name notification-service \
  -p 8095:8095 \
  --env-file .env \
  openride-notification-service:latest
```

### Docker Compose

```yaml
version: '3.8'

services:
  notification-service:
    build: .
    ports:
      - "8095:8095"
    env_file:
      - .env
    depends_on:
      - postgres
      - redis
    restart: unless-stopped

  celery-worker:
    build: .
    command: celery -A celery_app worker --loglevel=info
    env_file:
      - .env
    depends_on:
      - redis
    restart: unless-stopped

  celery-beat:
    build: .
    command: celery -A celery_app beat --loglevel=info
    env_file:
      - .env
    depends_on:
      - redis
    restart: unless-stopped
```

## Testing

Comprehensive test suite with >90% code coverage.

```bash
# Run all tests
pytest

# Run with coverage report
pytest --cov=app --cov-report=html --cov-report=term

# Run specific test file
pytest tests/test_notification_service.py -v
pytest tests/test_auth.py -v

# Run specific test class
pytest tests/test_api_notifications.py::TestNotificationAPI -v

# Run tests in parallel (faster)
pytest -n auto

# View HTML coverage report
open htmlcov/index.html  # macOS/Linux
start htmlcov/index.html  # Windows
```

### Test Structure

```
tests/
├── conftest.py                      # Fixtures & configuration
├── test_auth.py                     # JWT authentication (9 tests)
├── test_fcm_service.py             # FCM push (10 tests)
├── test_termii_service.py          # Termii SMS (12 tests)
├── test_email_service.py           # SendGrid email (13 tests)
├── test_template_service.py        # Templates (12 tests)
├── test_preference_service.py      # Preferences (11 tests)
├── test_notification_service.py    # Orchestration (11 tests)
├── test_api_notifications.py       # Notification API (7 tests)
├── test_api_tokens.py              # Token API (7 tests)
├── test_api_preferences.py         # Preferences API (8 tests)
├── test_api_templates.py           # Admin API (10 tests)
├── test_celery_tasks.py            # Async tasks (10 tests)
└── test_integration.py             # E2E tests (8 tests)

Total: 128+ tests
```

See [tests/README.md](tests/README.md) for detailed test documentation.

## Architecture

```
┌─────────────────┐
│   FastAPI App   │
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
┌───▼──┐  ┌──▼───┐
│ API  │  │Celery│
│Routes│  │Tasks │
└───┬──┘  └──┬───┘
    │        │
┌───▼────────▼───┐
│ Notification   │
│    Service     │
└───┬────────────┘
    │
┌───┴────────────┐
│ Channel        │
│ Services       │
├────────────────┤
│ • FCM Service  │
│ • SMS Service  │
│ • Email Service│
└────────────────┘
```

## Notification Types

- `booking_confirmed` - Booking created
- `payment_success` - Payment processed
- `payment_failed` - Payment failed
- `driver_arriving` - Driver approaching
- `trip_started` - Trip begun
- `trip_completed` - Trip finished
- `booking_cancelled` - Booking cancelled
- `route_cancelled` - Route cancelled
- `kyc_approved` - KYC approved
- `kyc_rejected` - KYC rejected
- `payout_processed` - Payout completed

## Monitoring

The service exposes Prometheus metrics on port 9095 (if enabled):

```
http://localhost:9095/metrics
```

## Support

For issues and questions, contact the OpenRide development team.

## License

Proprietary - OpenRide Platform

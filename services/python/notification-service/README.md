# OpenRide Notification Service

A comprehensive microservice for managing notifications across multiple channels (Push, SMS, Email) in the OpenRide platform.

## Features

- **Multi-Channel Notifications**
  - Push Notifications (Firebase Cloud Messaging)
  - SMS Notifications (Termii API for Nigeria)
  - Email Notifications (SendGrid)

- **Template Management**
  - Jinja2-based template rendering
  - Dynamic content substitution
  - Database-stored templates

- **User Preferences**
  - Per-user notification settings
  - Channel-specific enable/disable
  - Notification type filtering

- **Async Processing**
  - Celery-based task queue
  - Background notification delivery
  - Automatic retry logic

- **Delivery Tracking**
  - Complete notification history
  - Status tracking (pending, sent, delivered, failed)
  - Error logging

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
| `FCM_CREDENTIALS_PATH` | Firebase credentials file | `./config/firebase-credentials.json` |
| `TERMII_API_KEY` | Termii API key | - |
| `TERMII_SENDER_ID` | Termii sender ID | `OpenRide` |
| `SENDGRID_API_KEY` | SendGrid API key | - |
| `SENDGRID_FROM_EMAIL` | SendGrid from email | `noreply@openride.com` |

See `.env.example` for complete configuration options.

## API Endpoints

### Notifications

- `POST /v1/notifications/send` - Send notification (sync)
- `POST /v1/notifications/send-async` - Send notification (async via Celery)
- `POST /v1/notifications/broadcast` - Broadcast to multiple users
- `GET /v1/notifications/history` - Get user notification history
- `GET /v1/notifications/{id}` - Get notification details

### Device Tokens

- `POST /v1/notifications/tokens` - Register FCM token
- `GET /v1/notifications/tokens` - Get user's tokens
- `DELETE /v1/notifications/tokens/{id}` - Delete token

### Preferences

- `GET /v1/notifications/preferences` - Get user preferences
- `PATCH /v1/notifications/preferences` - Update preferences

### Admin Templates

- `POST /v1/admin/notification-templates` - Create template
- `GET /v1/admin/notification-templates` - List templates
- `GET /v1/admin/notification-templates/{id}` - Get template
- `PUT /v1/admin/notification-templates/{id}` - Update template
- `DELETE /v1/admin/notification-templates/{id}` - Delete template

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

```bash
# Run tests
pytest

# With coverage
pytest --cov=app --cov-report=html

# Run specific test file
pytest tests/test_notification_service.py -v
```

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

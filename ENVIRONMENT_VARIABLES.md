# Environment Variables Reference

This document lists all environment variables required to run the OpenRIDE backend services.
You can set these in your `docker-compose.yml`, a `.env` file, or your deployment environment (e.g., Kubernetes ConfigMaps/Secrets).

## Global / Shared Variables

These variables are commonly used across multiple services.

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL database host |
| `DB_PORT` | `5432` | PostgreSQL database port |
| `DB_NAME` | `openride` | Database name |
| `DB_USER` | `openride_user` | Database username |
| `DB_PASSWORD` | `openride_password` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | (empty) | Redis password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka bootstrap servers |
| `JWT_SECRET_KEY` | (change me) | Secret key for JWT signing (must be same across services) |

---

## Java Services

### 1. Auth Service (`auth-service`)
**Port:** 8081

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8081` | Service port |
| `LOG_LEVEL` | `DEBUG` | Logging level |
| `SQL_LOG_LEVEL` | `WARN` | Hibernate SQL logging level |
| `TWILIO_ACCOUNT_SID` | (empty) | Twilio Account SID for SMS |
| `TWILIO_AUTH_TOKEN` | (empty) | Twilio Auth Token |
| `TWILIO_PHONE_NUMBER` | (empty) | Twilio Phone Number |
| `USER_SERVICE_URL` | `http://localhost:8082/api` | URL of User Service |

### 2. User Service (`user-service`)
**Port:** 8082

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8082` | Service port |
| `ENCRYPTION_KEY` | (change me) | 32-char key for sensitive data encryption |

### 3. Booking Service (`booking-service`)
**Port:** 8083

| Variable | Default | Description |
|----------|---------|-------------|
| `REDIS_DB` | `3` | Redis database index |
| `KAFKA_SECURITY_PROTOCOL` | `PLAINTEXT` | Kafka security protocol |

### 4. Payments Service (`payments-service`)
**Port:** 8084

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8084` | Service port |
| `KORAPAY_API_URL` | `https://api.korapay.com` | Korapay API URL |
| `KORAPAY_SECRET_KEY` | `test_sk_xxx` | Korapay Secret Key |
| `KORAPAY_PUBLIC_KEY` | `test_pk_xxx` | Korapay Public Key |
| `KORAPAY_WEBHOOK_SECRET` | `test_webhook_secret` | Korapay Webhook Secret |
| `KORAPAY_MERCHANT_NAME` | `OpenRide` | Merchant Name |
| `BOOKING_SERVICE_URL` | `http://localhost:8083` | URL of Booking Service |

### 5. Payouts Service (`payouts-service`)
**Port:** 8087

| Variable | Default | Description |
|----------|---------|-------------|
| `DATABASE_URL` | `jdbc:postgresql://...` | Full JDBC URL (overrides DB_HOST/etc if set) |
| `DATABASE_USERNAME` | `postgres` | Database username |
| `DATABASE_PASSWORD` | `postgres` | Database password |
| `KAFKA_GROUP_ID` | `payouts-service` | Kafka consumer group ID |
| `SERVER_PORT` | `8087` | Service port |
| `PAYSTACK_API_KEY` | (required) | Paystack API Key |
| `PLATFORM_COMMISSION_RATE` | `0.15` | Commission rate (0.15 = 15%) |
| `MINIMUM_PAYOUT_AMOUNT` | `5000.00` | Min payout amount |
| `AUTO_SETTLEMENT_ENABLED` | `true` | Enable auto settlement |
| `SETTLEMENT_SCHEDULE_CRON` | `0 0 2 * * MON` | Cron for settlement |
| `PAYMENT_PROVIDER` | `PAYSTACK` | Payment provider name |

### 6. Ticketing Service (`ticketing-service`)
**Port:** 8086

| Variable | Default | Description |
|----------|---------|-------------|
| `TICKETING_BLOCKCHAIN_TYPE` | `POLYGON` | Blockchain type |
| `TICKETING_BLOCKCHAIN_RPC_URL` | `https://rpc-mumbai.maticvigil.com/` | RPC URL |
| `TICKETING_BLOCKCHAIN_PRIVATE_KEY` | (required) | Wallet private key |
| `TICKETING_BLOCKCHAIN_CONTRACT_ADDRESS` | (required) | Smart contract address |
| `TICKETING_CRYPTO_AUTO_GENERATE_KEYS` | `true` | Auto-generate crypto keys |

### 7. Admin Service (`admin-service`)
**Port:** 8086 (Note: Config says 8086, check for conflict with Ticketing)

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8086` | Service port |
| `JWT_SECRET` | (change me) | JWT Secret |

---

## Python Services

### 1. Driver Service (`driver-service`)
**Port:** 8000 (Default FastAPI) or configured

| Variable | Default | Description |
|----------|---------|-------------|
| `ENVIRONMENT` | `development` | App environment |
| `DEBUG` | `False` | Debug mode |
| `DATABASE_URL` | `postgresql+asyncpg://...` | Async Database URL |
| `REDIS_URL` | `redis://localhost:6379/0` | Redis URL |
| `CORS_ORIGINS` | `["http://localhost:3000"]` | Allowed CORS origins |
| `USER_SERVICE_URL` | `http://localhost:8081` | URL of User Service |

### 2. Matchmaking Service (`matchmaking-service`)
**Port:** 8084 (Config says 8084, check conflict with Payments)

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVICE_PORT` | `8084` | Service port |
| `SECRET_KEY` | (required) | Secret key |
| `DRIVER_SERVICE_URL` | (required) | URL of Driver Service |

### 3. Search Service (`search-service`)
**Port:** 8085

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVICE_PORT` | `8085` | Service port |
| `MATCHMAKING_SERVICE_URL` | (required) | URL of Matchmaking Service |

### 4. Notification Service (`notification-service`)
**Port:** 8095

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8095` | Service port |
| `CELERY_BROKER_URL` | `redis://.../4` | Celery broker URL |
| `CELERY_RESULT_BACKEND` | `redis://.../5` | Celery result backend |
| `FIREBASE_CREDENTIALS_PATH` | `./config/firebase-credentials.json` | Path to FCM creds |
| `TERMII_API_KEY` | (empty) | Termii API Key (SMS) |
| `SENDGRID_API_KEY` | (empty) | SendGrid API Key (Email) |

### 5. Analytics Service (`analytics-service`)
**Port:** 8097

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8097` | Service port |
| `CLICKHOUSE_HOST` | `localhost` | ClickHouse host |
| `CLICKHOUSE_PORT` | `8123` | ClickHouse port |
| `CLICKHOUSE_DATABASE` | `openride_analytics` | ClickHouse DB name |
| `SMTP_HOST` | `smtp.gmail.com` | SMTP Host |
| `SMTP_USER` | `noreply@openride.com` | SMTP User |
| `SMTP_PASSWORD` | (empty) | SMTP Password |

### 6. Fleet Service (`fleet-service`)
**Port:** 8096

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8096` | Service port |
| `SOCKETIO_PATH` | `/socket.io` | Socket.IO path |
| `METRICS_PORT` | `9096` | Metrics port |

---

## Docker Compose Example

To run all services, ensure your `docker-compose.yml` includes these environment variables. You can use a `.env` file in the same directory as `docker-compose.yml` to populate the `${VARIABLE}` placeholders.

**Example `.env` file:**

```dotenv
# Shared
DB_HOST=postgres
DB_PORT=5432
DB_USER=openride_user
DB_PASSWORD=openride_password
REDIS_HOST=redis
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
JWT_SECRET_KEY=super_secret_key_123

# Service Specific
PAYSTACK_API_KEY=pk_test_...
KORAPAY_SECRET_KEY=sk_test_...
TWILIO_AUTH_TOKEN=...
```

## ⚠️ Port Conflicts Detected

The following services are configured to use the same ports. You **must** change the port for one of the services in each pair (using the `PORT` or `SERVICE_PORT` environment variable) to avoid startup failures.

1.  **Port 8084:**
    *   `payments-service` (Java) defaults to 8084.
    *   `matchmaking-service` (Python) defaults to 8084.
    *   **Fix:** Set `SERVICE_PORT=8092` (or another unused port) for `matchmaking-service`.

2.  **Port 8086:**
    *   `ticketing-service` (Java) defaults to 8086.
    *   `admin-service` (Java) defaults to 8086.
    *   **Fix:** Set `SERVER_PORT=8088` (or another unused port) for `admin-service`.


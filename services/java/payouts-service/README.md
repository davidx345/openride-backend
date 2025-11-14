# OpenRide Payouts Service

## Overview

The Payouts Service manages driver earnings, commissions, wallet balances, and settlement processing for the OpenRide platform. It provides a complete financial management system with double-entry ledger accounting, bank account verification, and automated settlement processing.

## Features

### Core Functionality
- **Driver Wallet Management**: Track available balance, pending payouts, and total earnings
- **Earnings Ledger**: Double-entry accounting system for all financial transactions
- **Commission Calculation**: Configurable platform commission (default 15%)
- **Bank Account Verification**: Integration with Paystack/Flutterwave for account validation
- **Payout Requests**: Driver-initiated withdrawal requests with minimum threshold
- **Admin Approval Workflow**: Manual approval/rejection with notes
- **Settlement Processing**: Automated batch processing for approved payouts
- **Audit Trail**: Complete history of all financial transactions

### Financial Logic
- **Platform Commission**: 15% (configurable)
- **Driver Earnings**: trip_price × 0.85
- **Minimum Payout**: ₦5,000
- **Settlement Schedule**: Weekly/Bi-weekly (configurable)
- **Auto-settlement**: Optional automatic approval for verified drivers

## Architecture

```
Trip Completed (Kafka Event)
        ↓
Commission Calculator
        ↓
Earnings Ledger (CREDIT driver wallet)
        ↓
Driver Wallet (Available Balance)
        ↓
Payout Request (Driver initiated)
        ↓
Admin Approval (Manual/Automatic)
        ↓
Settlement Processor (Bank Transfer)
        ↓
Ledger Update (DEBIT wallet)
```

## Technology Stack

- **Framework**: Spring Boot 3.2.0
- **Language**: Java 17
- **Database**: PostgreSQL 14+ with Flyway migrations
- **Cache/Lock**: Redis (Redisson)
- **Messaging**: Apache Kafka
- **API Docs**: SpringDoc OpenAPI 3
- **Security**: Spring Security with JWT
- **Testing**: JUnit 5, Mockito, Testcontainers

## Database Schema

### Tables
1. **driver_wallets**: Driver wallet with balance tracking
2. **earnings_ledger**: Double-entry ledger for all transactions
3. **bank_accounts**: Driver bank account information
4. **payout_requests**: Withdrawal requests from drivers
5. **settlements**: Settlement batches and individual transfers
6. **audit_logs**: Complete audit trail

### Key Constraints
- Wallet balance cannot be negative (CHECK constraint)
- Ledger entries always balanced (trigger validation)
- One active payout request per driver
- Bank account verified before first payout

## API Endpoints

### Driver Endpoints
- `GET /v1/earnings` - Get earnings summary
- `GET /v1/earnings/history` - Get transaction history
- `POST /v1/bank-accounts` - Add bank account
- `GET /v1/bank-accounts` - List bank accounts
- `POST /v1/payouts/request` - Request payout
- `GET /v1/payouts/requests` - List payout requests

### Admin Endpoints
- `GET /v1/admin/payouts/pending` - List pending payouts
- `POST /v1/admin/payouts/{id}/approve` - Approve payout
- `POST /v1/admin/payouts/{id}/reject` - Reject payout
- `GET /v1/admin/settlements` - List settlement batches
- `POST /v1/admin/settlements/process` - Process settlement batch
- `GET /v1/admin/earnings/stats` - Platform-wide earnings statistics

## Kafka Events

### Consumed Events
- `trip.completed` - Process driver earnings when trip completes

### Published Events
- `payout.requested` - Driver requested payout
- `payout.approved` - Admin approved payout
- `payout.rejected` - Admin rejected payout
- `payout.completed` - Settlement successfully processed
- `payout.failed` - Settlement failed

## Configuration

### Environment Variables

```bash
# Application
SERVER_PORT=8087
SPRING_PROFILES_ACTIVE=development

# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/openride
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_GROUP_ID=payouts-service

# Financial Configuration
PLATFORM_COMMISSION_RATE=0.15
MINIMUM_PAYOUT_AMOUNT=5000.00
AUTO_SETTLEMENT_ENABLED=false
SETTLEMENT_SCHEDULE_CRON=0 0 2 * * MON

# Bank Verification
PAYSTACK_SECRET_KEY=sk_test_...
FLUTTERWAVE_SECRET_KEY=FLWSECK_TEST-...
BANK_VERIFICATION_PROVIDER=PAYSTACK

# Security
JWT_SECRET=your-secret-key-here
JWT_EXPIRATION=3600000
```

## Running Locally

### Prerequisites
- Java 17+
- Maven 3.8+
- PostgreSQL 14+
- Redis 7+
- Kafka (optional for local dev)

### Setup

1. **Start dependencies**:
```bash
docker-compose up -d postgres redis
```

2. **Run database migrations**:
```bash
mvn flyway:migrate
```

3. **Build the project**:
```bash
mvn clean install
```

4. **Run the service**:
```bash
mvn spring-boot:run
```

The service will be available at `http://localhost:8087`

### API Documentation

Access Swagger UI at: `http://localhost:8087/swagger-ui.html`

## Testing

### Run all tests
```bash
mvn test
```

### Run integration tests only
```bash
mvn test -Dtest=*IntegrationTest
```

### Test coverage
```bash
mvn jacoco:report
```

Coverage report: `target/site/jacoco/index.html`

## Security

### Authentication
All endpoints require JWT authentication except health checks.

### Authorization
- **Driver endpoints**: Require `DRIVER` role
- **Admin endpoints**: Require `ADMIN` role

### Data Protection
- Bank account numbers encrypted at rest (AES-256)
- Sensitive fields never logged
- PII data access logged in audit trail

## Monitoring

### Actuator Endpoints
- `/actuator/health` - Health check
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus metrics

### Key Metrics
- `payouts.requests.total` - Total payout requests
- `payouts.approved.total` - Approved payouts
- `settlements.processed.total` - Processed settlements
- `earnings.processed.total` - Total earnings processed
- `wallet.balance.total` - Total platform wallet balance

## Error Handling

### Standard Error Response
```json
{
  "timestamp": "2025-11-14T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient balance for payout request",
  "path": "/v1/payouts/request"
}
```

### Common Error Codes
- `400` - Validation error, insufficient balance
- `401` - Unauthorized (missing/invalid JWT)
- `403` - Forbidden (insufficient permissions)
- `404` - Resource not found
- `409` - Conflict (duplicate request, pending payout exists)
- `500` - Internal server error

## Business Rules

### Earnings Processing
1. Trip completed event received
2. Calculate platform commission (15%)
3. Calculate driver earnings (85%)
4. Create ledger entries (CREDIT driver, DEBIT platform)
5. Update driver wallet balance
6. Publish earning.processed event

### Payout Request
1. Driver initiates payout request
2. Validate minimum amount (₦5,000)
3. Validate sufficient balance
4. Validate no pending payout exists
5. Validate bank account verified
6. Create payout request (PENDING status)
7. Publish payout.requested event

### Payout Approval
1. Admin reviews payout request
2. Approve or reject with notes
3. Update payout status
4. If approved, add to settlement batch
5. Publish payout.approved/rejected event

### Settlement Processing
1. Scheduled job runs (weekly/bi-weekly)
2. Fetch all approved payouts
3. Create settlement batch
4. For each payout:
   - Call payment provider API
   - Create bank transfer
   - Update settlement status
5. Update wallet balance (DEBIT)
6. Create ledger entries
7. Publish payout.completed events

## Deployment

### Docker Build
```bash
docker build -t openride/payouts-service:latest .
```

### Docker Run
```bash
docker run -p 8087:8087 \
  -e DATABASE_URL=... \
  -e REDIS_HOST=... \
  -e KAFKA_BOOTSTRAP_SERVERS=... \
  openride/payouts-service:latest
```

### Kubernetes
See `infrastructure/kubernetes/payouts-service/` for manifests.

## Troubleshooting

### Common Issues

**Issue**: Duplicate earnings for same trip
- **Cause**: Kafka message reprocessing
- **Solution**: Check idempotency key handling, verify offset commits

**Issue**: Wallet balance mismatch
- **Cause**: Ledger entry error
- **Solution**: Run balance reconciliation job, check audit logs

**Issue**: Bank verification failing
- **Cause**: Invalid API keys or network issues
- **Solution**: Verify API keys, check provider status

**Issue**: Settlements not processing
- **Cause**: Distributed lock not releasing
- **Solution**: Check Redis locks, restart settlement job

## Contributing

See main repository `CONTRIBUTING.md` for guidelines.

## License

Proprietary - OpenRide Platform

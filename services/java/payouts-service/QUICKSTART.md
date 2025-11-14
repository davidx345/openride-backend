# Payouts Service - Quick Start Guide

## 🚀 Start the Service

```bash
# Using docker-compose (recommended)
docker-compose up -d payouts-service

# Or run locally
cd services/java/payouts-service
mvn spring-boot:run
```

## 🔍 Check Health

```bash
curl http://localhost:8087/actuator/health
```

## 📖 API Documentation

Open in browser: http://localhost:8087/swagger-ui.html

## 🧪 Test Endpoints

### 1. Get Wallet (as driver)

```bash
curl -X GET http://localhost:8087/v1/earnings/wallet \
  -H "X-Driver-Id: 550e8400-e29b-41d4-a716-446655440000"
```

### 2. Add Bank Account

```bash
curl -X POST http://localhost:8087/v1/bank-accounts \
  -H "X-Driver-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{
    "accountNumber": "0123456789",
    "bankCode": "058",
    "bankName": "GTBank"
  }'
```

### 3. Request Payout

```bash
curl -X POST http://localhost:8087/v1/payouts/request \
  -H "X-Driver-Id: 550e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 25000.00,
    "bankAccountId": "660e8400-e29b-41d4-a716-446655440000"
  }'
```

### 4. Get Pending Payouts (admin)

```bash
curl -X GET http://localhost:8087/v1/admin/payouts/pending \
  -H "X-Admin-Id: 770e8400-e29b-41d4-a716-446655440000"
```

### 5. Approve Payout (admin)

```bash
curl -X POST http://localhost:8087/v1/admin/payouts/{payoutId}/approve \
  -H "X-Admin-Id: 770e8400-e29b-41d4-a716-446655440000" \
  -H "Content-Type: application/json" \
  -d '{
    "notes": "Approved for settlement"
  }'
```

## 📊 Database Access

```bash
# Using docker exec
docker exec -it openride-postgres psql -U openride_user -d openride

# Check wallet balance
SELECT * FROM driver_wallets WHERE driver_id = '550e8400-e29b-41d4-a716-446655440000';

# Check ledger entries
SELECT * FROM earnings_ledger WHERE driver_id = '550e8400-e29b-41d4-a716-446655440000' ORDER BY created_at DESC LIMIT 10;

# Check payout requests
SELECT * FROM payout_requests ORDER BY created_at DESC LIMIT 10;
```

## 🔧 Configuration

Key environment variables:

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/openride
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres

# Paystack (required for bank verification & transfers)
PAYSTACK_API_KEY=sk_test_xxxxxxxxxxxxx

# Financial settings
PLATFORM_COMMISSION_RATE=0.15
MINIMUM_PAYOUT_AMOUNT=5000.00
SETTLEMENT_SCHEDULE_CRON="0 0 2 * * MON"
```

## 🐛 Troubleshooting

### Service won't start

1. Check if dependencies are running:
   ```bash
   docker ps | grep -E "postgres|redis|kafka"
   ```

2. Check logs:
   ```bash
   docker logs openride-payouts-service
   ```

### Kafka consumer not receiving messages

1. Check Kafka is healthy:
   ```bash
   docker logs openride-kafka
   ```

2. Verify topic exists:
   ```bash
   docker exec openride-kafka kafka-topics --list --bootstrap-server localhost:9092
   ```

### Bank account verification fails

1. Verify Paystack API key is set
2. Check Paystack API is reachable
3. Verify account number format (10 digits)

## 📈 Monitoring

- **Prometheus metrics:** http://localhost:9087/actuator/prometheus
- **Health check:** http://localhost:8087/actuator/health
- **Logs:** `docker logs -f openride-payouts-service`

## 🔑 Key Concepts

- **Commission:** 15% platform, 85% driver
- **Minimum payout:** ₦5,000
- **Settlement schedule:** Weekly (Monday 2:00 AM)
- **Payout states:** PENDING → APPROVED → PROCESSING → COMPLETED
- **Reserved funds:** Amount locked during payout processing

## 📞 Need Help?

See full documentation: `/docs/PHASE_9_SUMMARY.md`

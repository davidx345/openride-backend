# Phase 6 Ticketing Service - Quick Start Guide

## 🚀 Quick Start (5 Minutes)

### 1. Start Dependencies

```bash
cd c:/Users/USER/Documents/projects/openride-backend
docker-compose up -d postgres redis
```

### 2. Configure Environment

```bash
# Copy example config
cp services/java/ticketing-service/src/main/resources/application.yml.example \
   services/java/ticketing-service/src/main/resources/application.yml

# Edit with your settings (database, Redis, blockchain)
```

### 3. Build & Run

```bash
cd services/java/ticketing-service
mvn clean install
mvn spring-boot:run
```

### 4. Test API

```bash
# Health check
curl http://localhost:8086/actuator/health

# Get public key
curl http://localhost:8086/v1/tickets/public-key

# Swagger UI
open http://localhost:8086/swagger-ui.html
```

---

## 📋 Key API Endpoints

| Endpoint | Method | Purpose | Auth |
|----------|--------|---------|------|
| `/v1/tickets` | POST | Generate ticket | JWT |
| `/v1/bookings/{id}/ticket` | GET | Get ticket | JWT |
| `/v1/tickets/verify` | POST | Verify ticket | JWT |
| `/v1/tickets/public-key` | GET | Public key | None |
| `/actuator/health` | GET | Health check | None |

---

## 🔑 Key Configuration

```yaml
ticketing:
  crypto:
    private-key-path: /app/keys/private_key.pem
    public-key-path: /app/keys/public_key.pem
    auto-generate-keys: true
  
  blockchain:
    type: POLYGON
    rpc-url: https://rpc-mumbai.maticvigil.com/
    chain-id: 80001
  
  batch:
    max-size: 100
    anchoring-schedule: "0 0 * * * *"  # Hourly
  
  ticket:
    validity-hours: 24
    retention-days: 90
```

---

## 🧪 Testing

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=MerkleTreeTest

# With coverage
mvn test jacoco:report
```

---

## 📊 Monitoring

```bash
# Metrics
curl http://localhost:8086/actuator/metrics

# Health
curl http://localhost:8086/actuator/health

# Info
curl http://localhost:8086/actuator/info
```

---

## 🐛 Common Issues

### Keys not found
```bash
# Auto-generate on startup
export TICKETING_CRYPTO_AUTO_GENERATE_KEYS=true
```

### Blockchain connection fails
```bash
# Check RPC endpoint
curl https://rpc-mumbai.maticvigil.com/
```

### Database migration errors
```bash
# Reset database (dev only!)
mvn flyway:clean flyway:migrate
```

---

## 📚 Documentation

- **Full Guide**: `docs/PHASE_6_COMPLETE.md`
- **Architecture**: `services/java/ticketing-service/README.md`
- **API Docs**: `http://localhost:8086/swagger-ui.html`

---

## ✅ Verification Checklist

- [ ] PostgreSQL running on 5432
- [ ] Redis running on 6379
- [ ] Blockchain RPC accessible
- [ ] Keys generated/loaded
- [ ] Database migrations applied
- [ ] Service started on port 8086
- [ ] Health endpoint returns UP
- [ ] Public key endpoint returns PEM

---

**Need help?** Check `PHASE_6_COMPLETE.md` for detailed troubleshooting.

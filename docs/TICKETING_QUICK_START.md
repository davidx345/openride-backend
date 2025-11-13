# Ticketing Service - Quick Start Guide

## 🚀 Quick Start (5 Minutes)

### 1. Start the Stack

```bash
# From project root
docker-compose up -d

# Wait for services to be healthy
docker-compose ps
```

### 2. Verify Ticketing Service

```bash
# Health check
curl http://localhost:8086/actuator/health

# Expected response:
# {"status":"UP"}
```

### 3. Generate Your First Ticket

```bash
curl -X POST http://localhost:8086/api/v1/tickets \
  -H "Content-Type: application/json" \
  -d '{
    "bookingId": "test-booking-001",
    "userId": "test-user-001",
    "driverId": "test-driver-001",
    "vehicleId": "vehicle-001",
    "rideType": "STANDARD",
    "scheduledTime": "2024-12-15T10:00:00",
    "pickupLocation": "123 Main St",
    "dropoffLocation": "456 Oak Ave",
    "fare": 25.50,
    "paymentId": "payment-001",
    "paymentMethod": "CREDIT_CARD"
  }'
```

**Response**:
```json
{
  "ticketId": "ticket-abc123",
  "bookingId": "test-booking-001",
  "userId": "test-user-001",
  "driverId": "test-driver-001",
  "status": "ACTIVE",
  "qrCodeData": "eyJ0aWNrZXRJZCI6InRpY2tldC...",
  "signatureHex": "3045022100ab3d...",
  "validFrom": "2024-12-15T09:45:00",
  "validUntil": "2024-12-15T12:00:00",
  "issuedAt": "2024-12-15T09:45:23.456"
}
```

### 4. Get Ticket QR Code

```bash
# Save QR code image
curl http://localhost:8086/api/v1/tickets/ticket-abc123/qr \
  -o ticket.png

# Open image
open ticket.png  # macOS
# or
xdg-open ticket.png  # Linux
# or
start ticket.png  # Windows
```

### 5. Verify Ticket (Driver)

```bash
curl -X POST http://localhost:8086/api/v1/tickets/verify \
  -H "Content-Type: application/json" \
  -d '{
    "ticketId": "ticket-abc123",
    "driverId": "test-driver-001",
    "deviceId": "driver-phone-001",
    "latitude": 37.7749,
    "longitude": -122.4194
  }'
```

**Response**:
```json
{
  "valid": true,
  "ticketId": "ticket-abc123",
  "bookingId": "test-booking-001",
  "userId": "test-user-001",
  "status": "USED",
  "validationMessage": "Ticket verified successfully",
  "verifiedAt": "2024-12-15T09:50:12.345",
  "blockchainVerified": false
}
```

---

## 📋 Common Operations

### Get Ticket Details

```bash
curl http://localhost:8086/api/v1/tickets/{ticketId}
```

### List User's Tickets

```bash
curl "http://localhost:8086/api/v1/tickets?userId=test-user-001&status=ACTIVE"
```

### Cancel Ticket

```bash
curl -X DELETE http://localhost:8086/api/v1/tickets/{ticketId}
```

### Get Merkle Proof

```bash
curl http://localhost:8086/api/v1/tickets/{ticketId}/proof
```

---

## 🔗 Integration with Booking Service

### Add Dependency to Booking Service

**pom.xml**:
```xml
<dependency>
    <groupId>com.openride</groupId>
    <artifactId>java-commons</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Configure Service URL

**application.yml**:
```yaml
ticketing:
  service:
    url: http://ticketing-service:8086
```

### Generate Ticket After Payment

**BookingService.java**:
```java
@Autowired
private TicketingServiceClient ticketingClient;

public BookingConfirmationResponse confirmBooking(Booking booking, Payment payment) {
    // 1. Update booking status
    booking.setStatus("CONFIRMED");
    bookingRepository.save(booking);
    
    // 2. Generate ticket
    TicketGenerationRequest request = new TicketGenerationRequest();
    request.setBookingId(booking.getId());
    request.setUserId(booking.getUserId());
    request.setDriverId(booking.getDriverId());
    request.setScheduledTime(booking.getScheduledTime());
    request.setFare(booking.getFare());
    request.setPaymentId(payment.getId());
    
    TicketResponse ticket = ticketingClient.generateTicket(request);
    
    // 3. Link ticket to booking
    booking.setTicketId(ticket.getTicketId());
    bookingRepository.save(booking);
    
    return new BookingConfirmationResponse(booking, payment, ticket);
}
```

---

## 🐍 Integration with Driver Service (Python)

### Install Requirements

**requirements.txt**:
```
requests>=2.31.0
```

### Create Client

**ticketing_client.py**:
```python
import requests

class TicketingServiceClient:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip('/')
        
    def verify_ticket(self, ticket_id: str, driver_id: str, 
                     latitude: float, longitude: float, 
                     auth_token: str) -> dict:
        url = f"{self.base_url}/api/v1/tickets/verify"
        
        payload = {
            "ticketId": ticket_id,
            "driverId": driver_id,
            "latitude": latitude,
            "longitude": longitude
        }
        
        headers = {
            "Authorization": f"Bearer {auth_token}",
            "Content-Type": "application/json"
        }
        
        response = requests.post(url, json=payload, headers=headers)
        response.raise_for_status()
        return response.json()

# Usage
client = TicketingServiceClient("http://ticketing-service:8086")
result = client.verify_ticket(
    ticket_id="ticket-abc123",
    driver_id="driver-001",
    latitude=37.7749,
    longitude=-122.4194,
    auth_token=jwt_token
)

if result['valid']:
    print(f"Passenger: {result['userId']}")
    print(f"Booking: {result['bookingId']}")
else:
    print(f"Invalid: {result['validationMessage']}")
```

---

## 🧪 Testing

### Run Unit Tests

```bash
cd services/java/ticketing-service
mvn test
```

### Run Integration Tests

```bash
mvn test -Dtest=TicketingIntegrationTest
```

### Manual End-to-End Test

```bash
# 1. Generate
TICKET_ID=$(curl -s -X POST http://localhost:8086/api/v1/tickets \
  -H "Content-Type: application/json" \
  -d '{"bookingId":"test","userId":"user1","driverId":"driver1","scheduledTime":"2024-12-15T10:00:00","fare":25}' \
  | jq -r '.ticketId')

echo "Created ticket: $TICKET_ID"

# 2. Get details
curl http://localhost:8086/api/v1/tickets/$TICKET_ID | jq

# 3. Download QR
curl http://localhost:8086/api/v1/tickets/$TICKET_ID/qr -o ticket.png

# 4. Verify
curl -X POST http://localhost:8086/api/v1/tickets/verify \
  -H "Content-Type: application/json" \
  -d "{\"ticketId\":\"$TICKET_ID\",\"driverId\":\"driver1\"}" | jq

# 5. Check status (should be USED)
curl http://localhost:8086/api/v1/tickets/$TICKET_ID | jq '.status'
```

---

## 🔍 Debugging

### Check Logs

```bash
# Real-time logs
docker-compose logs -f ticketing-service

# Last 100 lines
docker-compose logs --tail=100 ticketing-service

# Filter for errors
docker-compose logs ticketing-service | grep ERROR
```

### Database Queries

```bash
# Connect to database
docker-compose exec postgres psql -U openride_user -d openride

# Check tickets
SELECT ticket_id, booking_id, status, created_at 
FROM tickets 
ORDER BY created_at DESC 
LIMIT 10;

# Check pending batches
SELECT batch_id, merkle_root, status, ticket_count 
FROM merkle_batches 
WHERE status = 'PENDING';

# Check verification logs
SELECT ticket_id, driver_id, result, verified_at 
FROM ticket_verification_logs 
ORDER BY verified_at DESC 
LIMIT 10;
```

### Redis Cache

```bash
# Connect to Redis
docker-compose exec redis redis-cli -a openride_redis_password

# List ticket keys
KEYS ticket:*

# Get cached ticket
GET ticket:ticket-abc123

# Clear cache
FLUSHDB
```

---

## ⚙️ Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/openride` | PostgreSQL URL |
| `SPRING_DATA_REDIS_HOST` | `redis` | Redis host |
| `TICKETING_BLOCKCHAIN_TYPE` | `POLYGON` | Blockchain network |
| `TICKETING_BLOCKCHAIN_RPC_URL` | Mumbai testnet | RPC endpoint |
| `TICKETING_CRYPTO_AUTO_GENERATE_KEYS` | `true` | Auto-generate crypto keys |
| `TICKETING_BATCH_SIZE` | `100` | Merkle batch size |
| `TICKETING_BATCH_INTERVAL_MINUTES` | `15` | Batch creation interval |

### Blockchain Configuration

For **production**, set these in docker-compose.yml:

```yaml
environment:
  TICKETING_BLOCKCHAIN_RPC_URL: https://polygon-rpc.com/
  TICKETING_BLOCKCHAIN_CHAIN_ID: 137  # Polygon mainnet
  TICKETING_BLOCKCHAIN_PRIVATE_KEY: ${POLYGON_PRIVATE_KEY}
  TICKETING_BLOCKCHAIN_CONTRACT_ADDRESS: ${CONTRACT_ADDRESS}
```

---

## 🚨 Troubleshooting

### Ticket Generation Fails

**Problem**: 500 error when generating ticket

**Solution**:
```bash
# Check database connectivity
curl http://localhost:8086/actuator/health/db

# Check logs for errors
docker-compose logs ticketing-service | grep "ERROR.*generateTicket"

# Verify booking exists (if using foreign keys)
docker-compose exec postgres psql -U openride_user -d openride \
  -c "SELECT * FROM bookings WHERE id = 'booking-123';"
```

### QR Code Not Generating

**Problem**: Empty or invalid QR code

**Solution**:
```bash
# Check if crypto keys exist
docker-compose exec ticketing-service ls -la /app/keys/

# If missing, restart service to regenerate
docker-compose restart ticketing-service

# Verify key generation in logs
docker-compose logs ticketing-service | grep "Generated new key pair"
```

### Blockchain Anchoring Stuck

**Problem**: Batches stuck in PENDING status

**Solution**:
```bash
# Check blockchain connectivity
curl http://localhost:8086/actuator/health/blockchain

# Check pending batches
docker-compose exec postgres psql -U openride_user -d openride \
  -c "SELECT * FROM merkle_batches WHERE status = 'PENDING';"

# Check RPC endpoint
curl -X POST https://rpc-mumbai.maticvigil.com/ \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}'

# Manually trigger batch job (development)
curl -X POST http://localhost:8086/actuator/scheduledtasks/batchAnchoringJob
```

### Verification Always Fails

**Problem**: All ticket verifications return `valid: false`

**Solution**:
```bash
# Check if ticket exists
curl http://localhost:8086/api/v1/tickets/{ticketId}

# Verify correct driver ID
# Ticket can only be verified by assigned driver

# Check ticket status (must be ACTIVE)
curl http://localhost:8086/api/v1/tickets/{ticketId} | jq '.status'

# Check verification logs
docker-compose exec postgres psql -U openride_user -d openride \
  -c "SELECT * FROM ticket_verification_logs WHERE ticket_id = 'ticket-abc123';"
```

---

## 📚 Documentation

- **[ARCHITECTURE.md](../services/java/ticketing-service/docs/ARCHITECTURE.md)** - System design
- **[API_REFERENCE.md](../services/java/ticketing-service/docs/API_REFERENCE.md)** - Complete API docs
- **[BLOCKCHAIN_INTEGRATION.md](../services/java/ticketing-service/docs/BLOCKCHAIN_INTEGRATION.md)** - Web3 guide
- **[DEPLOYMENT.md](../services/java/ticketing-service/docs/DEPLOYMENT.md)** - Production deployment
- **[INTEGRATION_GUIDE.md](../services/java/ticketing-service/docs/INTEGRATION_GUIDE.md)** - Service integration
- **[API_GATEWAY_SETUP.md](../services/java/ticketing-service/docs/API_GATEWAY_SETUP.md)** - Kong/NGINX config

---

## 🎯 Quick Tips

1. **Always check health** before debugging: `curl http://localhost:8086/actuator/health`
2. **Use Testcontainers** for integration tests (automatic PostgreSQL/Redis)
3. **Monitor batch jobs** via database: `SELECT * FROM merkle_batches`
4. **Cache QR codes** on client-side (they don't change)
5. **Use circuit breaker** when calling from Booking Service (already configured)
6. **Rate limit** ticket generation to prevent abuse (configured in API Gateway)

---

## 🔐 Security Checklist

- [ ] JWT authentication enabled on all endpoints
- [ ] HTTPS configured (TLS termination at gateway)
- [ ] Private keys secured (not in git)
- [ ] Database credentials rotated
- [ ] Redis password set
- [ ] Rate limiting configured
- [ ] Blockchain private key in secure vault
- [ ] Audit logging enabled
- [ ] CORS configured for allowed origins only

---

## 🚀 Production Deployment

Before deploying to production:

1. **Update blockchain config** - Use mainnet RPC and real private key
2. **Deploy smart contract** - Deploy Merkle root storage contract
3. **Set up monitoring** - Prometheus + Grafana
4. **Configure alerts** - PagerDuty/Slack for errors
5. **Enable backup** - Automated PostgreSQL backups
6. **Load test** - Test with 1000+ concurrent requests
7. **Review security** - Run security audit
8. **Update documentation** - Production runbook

See [DEPLOYMENT.md](../services/java/ticketing-service/docs/DEPLOYMENT.md) for complete production guide.

---

**Questions?** Check the full documentation in `services/java/ticketing-service/docs/`

# OpenRide Ticketing Service Integration Guide

## Overview

This guide describes how the Ticketing Service integrates with the OpenRide backend ecosystem, particularly with the Booking Service for end-to-end ride ticketing flow.

## Architecture

```
┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│   User App   │───>│API Gateway   │───>│  Booking     │───>│  Payment     │
└──────────────┘    │  (Kong)      │    │  Service     │    │  Service     │
                    └──────────────┘    └──────┬───────┘    └──────────────┘
                                               │
                                               ├─ Payment Success
                                               │
                                               v
                    ┌──────────────┐    ┌──────────────┐
                    │  Ticketing   │<───│  Generate    │
                    │  Service     │    │  Ticket      │
                    └──────┬───────┘    └──────────────┘
                           │
                           ├─ Store in PostgreSQL
                           ├─ Generate QR Code
                           ├─ Sign with ECDSA
                           ├─ Add to Merkle Batch
                           │
                           v
                    ┌──────────────┐
                    │  Blockchain  │
                    │  (Polygon)   │
                    └──────────────┘
```

## Integration Points

### 1. Booking Service → Ticketing Service

After successful payment confirmation, the Booking Service calls the Ticketing Service to generate a ticket.

#### Flow

1. **User books a ride** → Booking Service creates booking record
2. **Payment is processed** → Payment Service confirms payment
3. **Booking confirmed** → Booking Service calls Ticketing Service
4. **Ticket generated** → User receives ticket with QR code
5. **Driver pickup** → Driver scans QR code to verify ticket

#### Implementation in Booking Service

Add dependency to `booking-service/pom.xml`:

```xml
<dependency>
    <groupId>com.openride</groupId>
    <artifactId>java-commons</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Add configuration to `booking-service/application.yml`:

```yaml
ticketing:
  service:
    url: http://ticketing-service:8086
```

Inject `TicketingServiceClient` in booking confirmation logic:

```java
@Service
public class BookingService {
    
    private final TicketingServiceClient ticketingClient;
    
    @Autowired
    public BookingService(TicketingServiceClient ticketingClient) {
        this.ticketingClient = ticketingClient;
    }
    
    @Transactional
    public BookingConfirmationResponse confirmBooking(String bookingId, String paymentId) {
        // 1. Verify payment
        Payment payment = paymentService.getPayment(paymentId);
        if (!payment.getStatus().equals("CONFIRMED")) {
            throw new PaymentNotConfirmedException();
        }
        
        // 2. Update booking status
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        booking.setStatus("CONFIRMED");
        booking.setPaymentId(paymentId);
        bookingRepository.save(booking);
        
        // 3. Generate ticket
        try {
            TicketGenerationRequest ticketRequest = new TicketGenerationRequest();
            ticketRequest.setBookingId(booking.getId());
            ticketRequest.setUserId(booking.getUserId());
            ticketRequest.setDriverId(booking.getDriverId());
            ticketRequest.setVehicleId(booking.getVehicleId());
            ticketRequest.setRideType(booking.getRideType());
            ticketRequest.setScheduledTime(booking.getScheduledTime());
            ticketRequest.setPickupLocation(booking.getPickupLocation());
            ticketRequest.setDropoffLocation(booking.getDropoffLocation());
            ticketRequest.setFare(booking.getFare());
            ticketRequest.setPaymentId(paymentId);
            ticketRequest.setPaymentMethod(payment.getPaymentMethod());
            
            TicketResponse ticket = ticketingClient.generateTicket(ticketRequest);
            
            // 4. Store ticket reference in booking
            booking.setTicketId(ticket.getTicketId());
            bookingRepository.save(booking);
            
            // 5. Return confirmation with ticket
            return new BookingConfirmationResponse(
                booking,
                payment,
                ticket
            );
            
        } catch (TicketingServiceClient.TicketingServiceException e) {
            log.error("Failed to generate ticket for booking: {}", bookingId, e);
            // Still return confirmation, but flag ticket generation failure
            // Retry mechanism will generate ticket later
            return new BookingConfirmationResponse(
                booking,
                payment,
                null // Ticket will be generated by retry job
            );
        }
    }
}
```

#### Error Handling

If ticket generation fails:

1. **Booking is still confirmed** (payment was successful)
2. **Ticket generation is retried** via scheduled job
3. **User is notified** when ticket becomes available

Add retry job:

```java
@Scheduled(fixedDelay = 60000) // Every minute
public void retryFailedTickets() {
    List<Booking> bookingsWithoutTickets = bookingRepository
            .findByStatusAndTicketIdIsNull("CONFIRMED");
    
    for (Booking booking : bookingsWithoutTickets) {
        try {
            TicketGenerationRequest request = buildTicketRequest(booking);
            TicketResponse ticket = ticketingClient.generateTicket(request);
            booking.setTicketId(ticket.getTicketId());
            bookingRepository.save(booking);
            log.info("Retry successful: Generated ticket {} for booking {}", 
                    ticket.getTicketId(), booking.getId());
        } catch (Exception e) {
            log.warn("Retry failed for booking {}: {}", booking.getId(), e.getMessage());
        }
    }
}
```

### 2. Driver Service → Ticketing Service

When a driver picks up a passenger, they scan the QR code to verify the ticket.

#### Flow

1. **Driver arrives at pickup** → Opens verification screen
2. **Scans passenger's QR code** → Captures QR data
3. **Calls verification API** → Ticketing Service validates
4. **Verification result** → Driver sees passenger details
5. **Ride starts** → Ticket marked as USED

#### Implementation in Driver Service (Python)

Add client in `driver-service/ticketing_client.py`:

```python
import requests
from typing import Optional, Dict
import logging

logger = logging.getLogger(__name__)

class TicketingServiceClient:
    """Client for Ticketing Service API"""
    
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip('/')
        self.timeout = 10
        
    def verify_ticket(
        self,
        ticket_id: str,
        qr_code_data: str,
        signature: str,
        driver_id: str,
        device_id: str,
        latitude: float,
        longitude: float,
        auth_token: str
    ) -> Dict:
        """
        Verify a ticket for ride pickup.
        
        Args:
            ticket_id: Unique ticket identifier
            qr_code_data: QR code scanned from user's app
            signature: Digital signature from QR code
            driver_id: ID of driver performing verification
            device_id: Driver's device ID
            latitude: Pickup location latitude
            longitude: Pickup location longitude
            auth_token: JWT token for authentication
            
        Returns:
            Dictionary with verification result
        """
        url = f"{self.base_url}/api/v1/tickets/verify"
        
        headers = {
            "Authorization": f"Bearer {auth_token}",
            "Content-Type": "application/json"
        }
        
        payload = {
            "ticketId": ticket_id,
            "qrCodeData": qr_code_data,
            "signature": signature,
            "driverId": driver_id,
            "deviceId": device_id,
            "latitude": latitude,
            "longitude": longitude
        }
        
        try:
            response = requests.post(
                url,
                json=payload,
                headers=headers,
                timeout=self.timeout
            )
            response.raise_for_status()
            
            result = response.json()
            logger.info(f"Ticket verification: {result['valid']} for {ticket_id}")
            
            return result
            
        except requests.exceptions.RequestException as e:
            logger.error(f"Ticket verification failed: {e}")
            raise TicketingServiceException(f"Verification failed: {e}")
    
    def get_ticket(self, ticket_id: str, auth_token: str) -> Dict:
        """Get ticket details"""
        url = f"{self.base_url}/api/v1/tickets/{ticket_id}"
        
        headers = {
            "Authorization": f"Bearer {auth_token}"
        }
        
        try:
            response = requests.get(url, headers=headers, timeout=self.timeout)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to get ticket: {e}")
            raise TicketingServiceException(f"Get ticket failed: {e}")

class TicketingServiceException(Exception):
    """Exception for ticketing service errors"""
    pass
```

Use in driver verification endpoint:

```python
from fastapi import APIRouter, HTTPException, Depends
from .ticketing_client import TicketingServiceClient

router = APIRouter()
ticketing_client = TicketingServiceClient(os.getenv("TICKETING_SERVICE_URL"))

@router.post("/verify-ticket")
async def verify_pickup_ticket(
    request: TicketVerificationRequest,
    current_driver: Driver = Depends(get_current_driver)
):
    """Verify ticket when driver picks up passenger"""
    try:
        verification_result = ticketing_client.verify_ticket(
            ticket_id=request.ticket_id,
            qr_code_data=request.qr_code_data,
            signature=request.signature,
            driver_id=current_driver.id,
            device_id=request.device_id,
            latitude=request.latitude,
            longitude=request.longitude,
            auth_token=request.auth_token
        )
        
        if verification_result['valid']:
            # Start ride tracking
            ride = await start_ride(
                booking_id=verification_result['bookingId'],
                driver_id=current_driver.id,
                passenger_name=verification_result.get('userName')
            )
            
            return {
                "success": True,
                "message": "Ticket verified successfully",
                "passenger": {
                    "userId": verification_result['userId'],
                    "name": verification_result.get('userName'),
                    "bookingId": verification_result['bookingId']
                },
                "rideId": ride.id
            }
        else:
            return {
                "success": False,
                "message": verification_result['validationMessage']
            }
            
    except TicketingServiceException as e:
        raise HTTPException(status_code=503, detail=str(e))
```

### 3. Database Integration

#### Schema Dependencies

The Ticketing Service requires:

1. **booking_id references** from Booking Service
2. **user_id references** from User Service  
3. **driver_id references** from Driver Service (Python)

#### Flyway Migration Order

Ensure migrations run in correct order:

```
V1__user_schema.sql          (User Service)
V1__driver_schema.sql        (Driver Service - Python)
V1__booking_schema.sql       (Booking Service)
V1__payment_schema.sql       (Payment Service)
V1__ticketing_schema.sql     (Ticketing Service) <- Last
```

#### Foreign Key Configuration

In `V1__initial_schema.sql`:

```sql
-- Ticket references booking
ALTER TABLE tickets
ADD CONSTRAINT fk_ticket_booking
FOREIGN KEY (booking_id) REFERENCES bookings(id)
ON DELETE RESTRICT;

-- Ticket references user
ALTER TABLE tickets
ADD CONSTRAINT fk_ticket_user
FOREIGN KEY (user_id) REFERENCES users(id)
ON DELETE RESTRICT;

-- Note: driver_id is string reference (Python service uses MongoDB)
-- No foreign key constraint needed
```

### 4. API Gateway Configuration

See [API_GATEWAY_SETUP.md](./API_GATEWAY_SETUP.md) for detailed Kong/NGINX configuration.

#### Route Summary

| Endpoint | Method | Service | Auth | Rate Limit |
|----------|--------|---------|------|------------|
| `/api/v1/tickets` | POST | Ticketing | JWT | 100/min |
| `/api/v1/tickets/{id}` | GET | Ticketing | JWT | None |
| `/api/v1/tickets/verify` | POST | Ticketing | JWT | 200/min |
| `/api/v1/tickets/{id}/qr` | GET | Ticketing | JWT | 50/min |
| `/api/v1/tickets/{id}/proof` | GET | Ticketing | JWT | None |

### 5. Event-Driven Integration (Future)

For Phase 3+, integrate with Kafka for event-driven architecture:

#### Events Published

```java
// When ticket is generated
@KafkaListener(topic = "ticket.generated")
public void onTicketGenerated(TicketGeneratedEvent event) {
    // Notification Service sends ticket to user
    // Analytics Service tracks ticket metrics
}

// When ticket is verified
@KafkaListener(topic = "ticket.verified")
public void onTicketVerified(TicketVerifiedEvent event) {
    // Ride tracking starts
    // Driver Service updates ride status
}

// When ticket is cancelled
@KafkaListener(topic = "ticket.cancelled")
public void onTicketCancelled(TicketCancelledEvent event) {
    // Refund processing
    // Analytics update
}
```

## Deployment

### Docker Compose

The complete stack is orchestrated via docker-compose.yml:

```bash
# Start all services
docker-compose up -d

# Check ticketing service logs
docker-compose logs -f ticketing-service

# Scale ticketing service
docker-compose up -d --scale ticketing-service=3

# Stop all services
docker-compose down
```

### Environment Variables

Required environment variables for ticketing-service:

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/openride
SPRING_DATASOURCE_USERNAME=openride_user
SPRING_DATASOURCE_PASSWORD=openride_password

# Redis
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=openride_redis_password

# Blockchain
TICKETING_BLOCKCHAIN_TYPE=POLYGON
TICKETING_BLOCKCHAIN_RPC_URL=https://polygon-rpc.com/
TICKETING_BLOCKCHAIN_PRIVATE_KEY=<your-private-key>
TICKETING_BLOCKCHAIN_CONTRACT_ADDRESS=<contract-address>
TICKETING_BLOCKCHAIN_CHAIN_ID=137

# Cryptography
TICKETING_CRYPTO_PRIVATE_KEY_PATH=/app/keys/private_key.pem
TICKETING_CRYPTO_PUBLIC_KEY_PATH=/app/keys/public_key.pem
TICKETING_CRYPTO_AUTO_GENERATE_KEYS=false
```

### Health Checks

Monitor service health:

```bash
# Ticketing service health
curl http://localhost:8086/actuator/health

# Database connectivity
curl http://localhost:8086/actuator/health/db

# Redis connectivity
curl http://localhost:8086/actuator/health/redis

# Blockchain connectivity
curl http://localhost:8086/actuator/health/blockchain
```

## Testing

### End-to-End Test

Run full integration test:

```bash
cd services/java/ticketing-service
mvn test -Dtest=TicketingIntegrationTest
```

### Manual Testing

1. **Generate ticket**:
```bash
curl -X POST http://localhost:8086/api/v1/tickets \
  -H "Content-Type: application/json" \
  -d '{
    "bookingId": "booking-123",
    "userId": "user-456",
    "driverId": "driver-789",
    "scheduledTime": "2024-12-15T10:00:00",
    "fare": 25.50
  }'
```

2. **Verify ticket**:
```bash
curl -X POST http://localhost:8086/api/v1/tickets/verify \
  -H "Content-Type: application/json" \
  -d '{
    "ticketId": "ticket-abc",
    "driverId": "driver-789"
  }'
```

## Monitoring

### Metrics

Key metrics to monitor:

- **Ticket generation rate**: Tickets/minute
- **Verification success rate**: % valid verifications
- **Blockchain anchoring latency**: Time to anchor batch
- **QR code generation time**: Milliseconds
- **Database query performance**: Query execution time

### Alerts

Configure alerts for:

- ✗ Ticket generation failures > 5%
- ✗ Blockchain RPC errors
- ✗ Database connection failures
- ✗ Redis cache misses > 20%
- ✗ Merkle batch size > 1000 tickets

## Troubleshooting

### Ticket Generation Fails

```bash
# Check logs
docker-compose logs ticketing-service | grep ERROR

# Verify database connectivity
docker-compose exec ticketing-service curl http://localhost:8086/actuator/health/db

# Check booking exists
docker-compose exec postgres psql -U openride_user -d openride \
  -c "SELECT * FROM bookings WHERE id = 'booking-123';"
```

### Blockchain Anchoring Issues

```bash
# Check blockchain connectivity
curl http://localhost:8086/actuator/health/blockchain

# View pending batches
docker-compose exec postgres psql -U openride_user -d openride \
  -c "SELECT * FROM merkle_batches WHERE status = 'PENDING';"

# Manually trigger batch job (development only)
curl -X POST http://localhost:8086/actuator/scheduledtasks/batchAnchoringJob
```

### QR Code Not Generating

```bash
# Check key files exist
docker-compose exec ticketing-service ls -la /app/keys/

# Regenerate keys if needed
docker-compose exec ticketing-service \
  curl -X POST http://localhost:8086/api/v1/admin/regenerate-keys
```

## Security Considerations

1. **JWT Validation**: All endpoints require valid JWT tokens
2. **Rate Limiting**: Prevent ticket generation abuse
3. **Private Key Security**: Store blockchain private key securely (AWS KMS, HashiCorp Vault)
4. **HTTPS**: Use TLS in production
5. **Database Encryption**: Encrypt sensitive fields (ticket_hash, signature)
6. **Audit Logging**: Log all ticket operations for compliance

## Performance Optimization

1. **Connection Pooling**: PostgreSQL connection pool (min=5, max=20)
2. **Redis Caching**: Cache ticket lookups (TTL=300s)
3. **Batch Processing**: Merkle batches of 100-500 tickets
4. **Async Blockchain Calls**: Don't block ticket generation
5. **Database Indexing**: Indexes on booking_id, user_id, status

## Next Steps

1. ✓ Docker Compose integration complete
2. ✓ Service communication DTOs created
3. → Implement Booking Service integration
4. → Configure API Gateway (Kong)
5. → Run integration tests
6. → Deploy to staging environment
7. → Load testing (1000 tickets/minute)
8. → Production deployment

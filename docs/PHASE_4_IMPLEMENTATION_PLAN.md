# Phase 4 Implementation Plan - Booking & Seat Inventory Management

## Overview

**Phase**: 4 - Booking & Seat Inventory Management  
**Service**: Booking Service (Java Spring Boot)  
**Priority**: ⭐ **CRITICAL** - Core revenue-generating functionality  
**Performance Target**: Mean latency < 150ms, 99.9% availability  
**Status**: 🚧 IN PROGRESS

---

## Business Requirements

### Core Functionality
1. **Seat Reservation**: Riders can book seats on active routes
2. **Inventory Management**: Prevent double-booking with ACID guarantees
3. **Temporary Holds**: Reserve seats during payment (10-minute TTL)
4. **Payment Integration**: Confirm bookings after successful payment
5. **Cancellation**: Support cancellations with refund calculations
6. **Concurrency Safety**: Handle 100+ concurrent bookings without conflicts

### Critical Constraints
- **No Double Bookings**: Absolute requirement - use distributed locks + DB transactions
- **Seat Hold TTL**: 10 minutes default (configurable)
- **Idempotency**: Retry-safe operations with idempotency keys
- **State Consistency**: Bookings must maintain valid state transitions
- **Performance**: < 150ms mean latency for booking creation

---

## Architecture Design

### Technology Stack
- **Framework**: Spring Boot 3.2+ with Java 21
- **Database**: PostgreSQL 14+ with ACID transactions
- **Cache/Locks**: Redis 7+ (Redisson for distributed locks)
- **Messaging**: Kafka for event publishing
- **Testing**: JUnit 5, Testcontainers, MockMVC

### Service Dependencies
- **User Service**: Verify rider exists and is active
- **Driver Service**: Fetch route details, stops, pricing
- **Payment Service**: Initiate payments, receive webhooks
- **Notification Service**: Send booking confirmations
- **Ticketing Service**: Generate tickets after confirmation

---

## Database Schema Design

### Table: bookings

```sql
CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_reference VARCHAR(20) UNIQUE NOT NULL, -- e.g., "BK20250113AB12"
    rider_id UUID NOT NULL,
    route_id UUID NOT NULL,
    driver_id UUID NOT NULL, -- Denormalized for quick access
    
    -- Journey details
    origin_stop_id UUID NOT NULL,
    destination_stop_id UUID NOT NULL,
    travel_date DATE NOT NULL,
    departure_time TIME NOT NULL,
    
    -- Pricing
    seats_booked INT NOT NULL CHECK (seats_booked > 0 AND seats_booked <= 4),
    price_per_seat DECIMAL(10, 2) NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    platform_fee DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    
    -- Status tracking
    status VARCHAR(30) NOT NULL, -- PENDING, HELD, PAYMENT_INITIATED, PAID, CONFIRMED, etc.
    payment_id UUID, -- Foreign key to payments service
    payment_status VARCHAR(20), -- PENDING, SUCCESS, FAILED, REFUNDED
    
    -- Seat allocation
    seat_numbers INT[] NOT NULL, -- e.g., [1, 2, 3]
    
    -- Metadata
    idempotency_key VARCHAR(100) UNIQUE,
    booking_source VARCHAR(20) DEFAULT 'WEB', -- WEB, MOBILE, API
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP, -- For PENDING/HELD bookings (created_at + 10min)
    confirmed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    
    -- Cancellation
    cancellation_reason TEXT,
    refund_amount DECIMAL(10, 2),
    refund_status VARCHAR(20), -- NONE, PENDING, PROCESSED, FAILED
    
    -- Audit
    created_by UUID, -- User who created (rider_id usually)
    updated_by UUID,
    
    CONSTRAINT fk_rider FOREIGN KEY (rider_id) REFERENCES users(id),
    CONSTRAINT fk_route FOREIGN KEY (route_id) REFERENCES routes(id),
    CONSTRAINT fk_origin_stop FOREIGN KEY (origin_stop_id) REFERENCES stops(id),
    CONSTRAINT fk_destination_stop FOREIGN KEY (destination_stop_id) REFERENCES stops(id)
);

-- Indexes
CREATE INDEX idx_bookings_rider ON bookings(rider_id);
CREATE INDEX idx_bookings_route_date ON bookings(route_id, travel_date);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_bookings_expires_at ON bookings(expires_at) WHERE status IN ('PENDING', 'HELD');
CREATE INDEX idx_bookings_reference ON bookings(booking_reference);
CREATE INDEX idx_bookings_idempotency ON bookings(idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_bookings_payment ON bookings(payment_id) WHERE payment_id IS NOT NULL;

-- Function: Generate booking reference
CREATE OR REPLACE FUNCTION generate_booking_reference()
RETURNS VARCHAR(20) AS $$
DECLARE
    ref VARCHAR(20);
BEGIN
    ref := 'BK' || TO_CHAR(NOW(), 'YYYYMMDD') || UPPER(SUBSTRING(MD5(RANDOM()::TEXT) FROM 1 FOR 6));
    RETURN ref;
END;
$$ LANGUAGE plpgsql;
```

### Table: booking_status_history

```sql
CREATE TABLE booking_status_history (
    id BIGSERIAL PRIMARY KEY,
    booking_id UUID NOT NULL,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    reason TEXT,
    metadata JSONB, -- Additional context
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by UUID,
    
    CONSTRAINT fk_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE
);

CREATE INDEX idx_booking_status_history_booking ON booking_status_history(booking_id);
CREATE INDEX idx_booking_status_history_created_at ON booking_status_history(created_at);
```

### Table: seat_holds (Optional - Redis Primary)

```sql
-- Backup table for Redis seat holds (for reconciliation)
CREATE TABLE seat_holds (
    id BIGSERIAL PRIMARY KEY,
    route_id UUID NOT NULL,
    travel_date DATE NOT NULL,
    seat_number INT NOT NULL,
    booking_id UUID NOT NULL,
    held_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    released_at TIMESTAMP,
    
    CONSTRAINT unique_seat_hold UNIQUE (route_id, travel_date, seat_number, booking_id)
);

CREATE INDEX idx_seat_holds_route_date ON seat_holds(route_id, travel_date);
CREATE INDEX idx_seat_holds_expires_at ON seat_holds(expires_at) WHERE released_at IS NULL;
```

---

## Booking State Machine

### States

```
PENDING           → Initial state when booking created
HELD              → Seats temporarily held in Redis
PAYMENT_INITIATED → Payment process started with provider
PAID              → Payment confirmed by provider
CONFIRMED         → Booking finalized, ticket generated
CHECKED_IN        → Rider checked in at pickup point
COMPLETED         → Trip finished successfully
CANCELLED         → Booking cancelled (before/after payment)
REFUNDED          → Money returned to rider
EXPIRED           → Booking expired without payment
```

### Allowed Transitions

```java
public enum BookingStatus {
    PENDING,
    HELD,
    PAYMENT_INITIATED,
    PAID,
    CONFIRMED,
    CHECKED_IN,
    COMPLETED,
    CANCELLED,
    REFUNDED,
    EXPIRED;
    
    // Valid state transitions
    public static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS = Map.of(
        PENDING, Set.of(HELD, CANCELLED, EXPIRED),
        HELD, Set.of(PAYMENT_INITIATED, CANCELLED, EXPIRED),
        PAYMENT_INITIATED, Set.of(PAID, CANCELLED, EXPIRED),
        PAID, Set.of(CONFIRMED, REFUNDED),
        CONFIRMED, Set.of(CHECKED_IN, CANCELLED, REFUNDED),
        CHECKED_IN, Set.of(COMPLETED, CANCELLED),
        COMPLETED, Set.of(), // Terminal state
        CANCELLED, Set.of(REFUNDED),
        REFUNDED, Set.of(), // Terminal state
        EXPIRED, Set.of() // Terminal state
    );
}
```

### State Transition Logic

```java
public void transitionTo(BookingStatus newStatus, String reason) {
    Set<BookingStatus> allowedNext = ALLOWED_TRANSITIONS.get(this.status);
    
    if (!allowedNext.contains(newStatus)) {
        throw new InvalidStateTransitionException(
            "Cannot transition from " + this.status + " to " + newStatus
        );
    }
    
    // Record history
    bookingStatusHistoryRepository.save(
        new BookingStatusHistory(this.id, this.status, newStatus, reason)
    );
    
    // Update status
    this.status = newStatus;
    this.updatedAt = Instant.now();
}
```

---

## Redis Seat-Hold Mechanism

### Key Patterns

```
# Individual seat hold
seat:hold:{route_id}:{travel_date}:{seat_number} → {booking_id}
TTL: 600 seconds (10 minutes)

# Booking metadata
booking:hold:{booking_id} → JSON { route_id, seat_numbers[], expires_at }
TTL: 600 seconds (10 minutes)

# Route available seats cache
route:seats:{route_id}:{travel_date} → SET[1,2,3,4,...,N]
TTL: 300 seconds (5 minutes)
```

### Operations

```java
// 1. Hold Seats
public boolean holdSeats(UUID routeId, LocalDate travelDate, 
                         List<Integer> seatNumbers, UUID bookingId) {
    
    String lockKey = "lock:route:" + routeId + ":" + travelDate;
    RLock lock = redissonClient.getLock(lockKey);
    
    try {
        // Acquire distributed lock (wait max 5s, auto-release after 10s)
        if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
            throw new SeatHoldException("Failed to acquire lock for route");
        }
        
        // Check if seats already held
        for (Integer seatNumber : seatNumbers) {
            String seatKey = buildSeatKey(routeId, travelDate, seatNumber);
            if (redisTemplate.hasKey(seatKey)) {
                return false; // Seat already held
            }
        }
        
        // Hold all seats atomically
        for (Integer seatNumber : seatNumbers) {
            String seatKey = buildSeatKey(routeId, travelDate, seatNumber);
            redisTemplate.opsForValue().set(
                seatKey, 
                bookingId.toString(), 
                Duration.ofMinutes(10)
            );
        }
        
        // Store booking metadata
        String bookingKey = "booking:hold:" + bookingId;
        SeatHoldMetadata metadata = new SeatHoldMetadata(
            routeId, travelDate, seatNumbers, Instant.now().plusSeconds(600)
        );
        redisTemplate.opsForValue().set(
            bookingKey,
            objectMapper.writeValueAsString(metadata),
            Duration.ofMinutes(10)
        );
        
        return true;
        
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new SeatHoldException("Lock acquisition interrupted", e);
    } finally {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}

// 2. Release Seats
public void releaseSeats(UUID routeId, LocalDate travelDate, 
                         List<Integer> seatNumbers, UUID bookingId) {
    
    for (Integer seatNumber : seatNumbers) {
        String seatKey = buildSeatKey(routeId, travelDate, seatNumber);
        String heldBy = redisTemplate.opsForValue().get(seatKey);
        
        // Only release if held by this booking
        if (bookingId.toString().equals(heldBy)) {
            redisTemplate.delete(seatKey);
        }
    }
    
    // Delete booking metadata
    redisTemplate.delete("booking:hold:" + bookingId);
}

// 3. Extend Hold (when payment initiated)
public boolean extendHold(UUID bookingId, Duration extension) {
    String bookingKey = "booking:hold:" + bookingId;
    String metadataJson = redisTemplate.opsForValue().get(bookingKey);
    
    if (metadataJson == null) {
        return false; // Hold already expired
    }
    
    SeatHoldMetadata metadata = objectMapper.readValue(
        metadataJson, SeatHoldMetadata.class
    );
    
    // Extend all seat holds
    for (Integer seatNumber : metadata.getSeatNumbers()) {
        String seatKey = buildSeatKey(
            metadata.getRouteId(), 
            metadata.getTravelDate(), 
            seatNumber
        );
        redisTemplate.expire(seatKey, extension);
    }
    
    // Extend booking metadata
    redisTemplate.expire(bookingKey, extension);
    
    return true;
}

// 4. Check Availability (DB + Redis)
public List<Integer> getAvailableSeats(UUID routeId, LocalDate travelDate) {
    Route route = routeRepository.findById(routeId)
        .orElseThrow(() -> new RouteNotFoundException(routeId));
    
    int totalSeats = route.getSeatsAvailable();
    Set<Integer> allSeats = IntStream.rangeClosed(1, totalSeats)
        .boxed()
        .collect(Collectors.toSet());
    
    // Remove confirmed bookings from DB
    List<Booking> confirmedBookings = bookingRepository
        .findByRouteIdAndTravelDateAndStatusIn(
            routeId, 
            travelDate, 
            List.of(BookingStatus.CONFIRMED, BookingStatus.CHECKED_IN)
        );
    
    for (Booking booking : confirmedBookings) {
        allSeats.removeAll(booking.getSeatNumbers());
    }
    
    // Remove Redis holds
    for (Integer seat : new ArrayList<>(allSeats)) {
        String seatKey = buildSeatKey(routeId, travelDate, seat);
        if (redisTemplate.hasKey(seatKey)) {
            allSeats.remove(seat);
        }
    }
    
    return new ArrayList<>(allSeats);
}
```

---

## Distributed Lock Strategy

### Redisson Configuration

```java
@Configuration
public class RedissonConfig {
    
    @Bean
    public RedissonClient redissonClient(
        @Value("${spring.redis.host}") String host,
        @Value("${spring.redis.port}") int port
    ) {
        Config config = new Config();
        config.useSingleServer()
            .setAddress("redis://" + host + ":" + port)
            .setConnectionPoolSize(50)
            .setConnectionMinimumIdleSize(10)
            .setRetryAttempts(3)
            .setRetryInterval(1500);
        
        return Redisson.create(config);
    }
}
```

### Lock Patterns

```java
@Service
public class DistributedLockService {
    
    private final RedissonClient redissonClient;
    
    public <T> T executeWithLock(String lockKey, 
                                  long waitTime, 
                                  long leaseTime, 
                                  TimeUnit timeUnit,
                                  Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, timeUnit);
            
            if (!acquired) {
                throw new LockAcquisitionException(
                    "Failed to acquire lock: " + lockKey
                );
            }
            
            return action.get();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Lock interrupted", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

// Usage
UUID bookingId = distributedLockService.executeWithLock(
    "lock:route:" + routeId + ":" + travelDate,
    5, // wait max 5 seconds
    10, // auto-release after 10 seconds
    TimeUnit.SECONDS,
    () -> createBookingInternal(request)
);
```

---

## Booking Creation Flow

### Sequence Diagram

```
Rider → BookingController: POST /v1/bookings
  ↓
BookingController → BookingService: createBooking(request)
  ↓
BookingService → RouteService: validateRoute(routeId)
  ↓
BookingService → SeatAvailabilityService: checkAvailability(routeId, seats, date)
  ↓
BookingService → DistributedLockService: executeWithLock("lock:route:...")
  ↓
BookingService → BookingRepository: save(booking) [PENDING status]
  ↓
BookingService → SeatHoldService: holdSeats(routeId, seats, bookingId)
  ↓
BookingService → BookingRepository: update(booking) [HELD status]
  ↓
BookingService → PaymentService: initiatePayment(bookingId, amount)
  ↓
PaymentService → BookingService: return paymentUrl
  ↓
BookingController → Rider: { booking, paymentUrl }
```

### Implementation

```java
@Service
@Transactional
public class BookingService {
    
    public BookingResponse createBooking(CreateBookingRequest request) {
        
        // 1. Validate inputs
        validateBookingRequest(request);
        
        // 2. Check idempotency
        if (request.getIdempotencyKey() != null) {
            Optional<Booking> existing = bookingRepository
                .findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }
        
        // 3. Validate route and pricing
        Route route = routeService.getRouteById(request.getRouteId());
        validateRouteActive(route);
        
        // 4. Calculate pricing
        BigDecimal totalPrice = pricingService.calculatePrice(
            request.getRouteId(),
            request.getOriginStopId(),
            request.getDestinationStopId(),
            request.getSeatsBooked()
        );
        
        // 5. Execute with distributed lock
        String lockKey = "lock:route:" + request.getRouteId() + ":" + request.getTravelDate();
        
        return distributedLockService.executeWithLock(
            lockKey, 5, 10, TimeUnit.SECONDS,
            () -> {
                // 6. Check seat availability
                List<Integer> availableSeats = seatAvailabilityService
                    .getAvailableSeats(request.getRouteId(), request.getTravelDate());
                
                if (availableSeats.size() < request.getSeatsBooked()) {
                    throw new InsufficientSeatsException(
                        "Only " + availableSeats.size() + " seats available"
                    );
                }
                
                // 7. Allocate seats
                List<Integer> allocatedSeats = availableSeats.subList(
                    0, request.getSeatsBooked()
                );
                
                // 8. Create booking in PENDING status
                Booking booking = Booking.builder()
                    .bookingReference(generateBookingReference())
                    .riderId(request.getRiderId())
                    .routeId(request.getRouteId())
                    .driverId(route.getDriverId())
                    .originStopId(request.getOriginStopId())
                    .destinationStopId(request.getDestinationStopId())
                    .travelDate(request.getTravelDate())
                    .departureTime(route.getDepartureTime())
                    .seatsBooked(request.getSeatsBooked())
                    .seatNumbers(allocatedSeats)
                    .pricePerSeat(totalPrice.divide(
                        BigDecimal.valueOf(request.getSeatsBooked()), 
                        RoundingMode.HALF_UP
                    ))
                    .totalPrice(totalPrice)
                    .platformFee(calculatePlatformFee(totalPrice))
                    .status(BookingStatus.PENDING)
                    .idempotencyKey(request.getIdempotencyKey())
                    .expiresAt(Instant.now().plusSeconds(600)) // 10 min
                    .build();
                
                booking = bookingRepository.save(booking);
                
                // 9. Hold seats in Redis
                boolean held = seatHoldService.holdSeats(
                    request.getRouteId(),
                    request.getTravelDate(),
                    allocatedSeats,
                    booking.getId()
                );
                
                if (!held) {
                    throw new SeatHoldException("Failed to hold seats");
                }
                
                // 10. Transition to HELD status
                booking.transitionTo(BookingStatus.HELD, "Seats held successfully");
                booking = bookingRepository.save(booking);
                
                // 11. Initiate payment
                PaymentInitiationResponse paymentResponse = paymentService
                    .initiatePayment(PaymentRequest.builder()
                        .bookingId(booking.getId())
                        .amount(totalPrice)
                        .riderId(request.getRiderId())
                        .description("Booking " + booking.getBookingReference())
                        .callbackUrl(buildCallbackUrl(booking.getId()))
                        .build()
                    );
                
                // 12. Update booking with payment ID
                booking.setPaymentId(paymentResponse.getPaymentId());
                booking.transitionTo(
                    BookingStatus.PAYMENT_INITIATED, 
                    "Payment initiated"
                );
                booking = bookingRepository.save(booking);
                
                // 13. Extend hold since payment started
                seatHoldService.extendHold(
                    booking.getId(), 
                    Duration.ofMinutes(15)
                );
                
                // 14. Publish event
                publishBookingCreatedEvent(booking);
                
                // 15. Return response
                return BookingResponse.builder()
                    .booking(toDTO(booking))
                    .paymentUrl(paymentResponse.getPaymentUrl())
                    .expiresAt(booking.getExpiresAt())
                    .build();
            }
        );
    }
}
```

---

## Payment Integration & Confirmation

### Webhook Handler

```java
@RestController
@RequestMapping("/v1/webhooks")
public class PaymentWebhookController {
    
    @PostMapping("/payments")
    public ResponseEntity<Void> handlePaymentWebhook(
        @RequestHeader("X-Payment-Signature") String signature,
        @RequestBody PaymentWebhookPayload payload
    ) {
        
        // 1. Verify signature
        if (!paymentService.verifyWebhookSignature(payload, signature)) {
            log.warn("Invalid webhook signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // 2. Check idempotency
        String idempotencyKey = "webhook:payment:" + payload.getPaymentId();
        if (!idempotencyService.isFirstRequest(idempotencyKey)) {
            log.info("Duplicate webhook ignored");
            return ResponseEntity.ok().build();
        }
        
        // 3. Process payment result
        try {
            if ("SUCCESS".equals(payload.getStatus())) {
                bookingService.confirmBooking(
                    payload.getBookingId(),
                    payload.getPaymentId()
                );
            } else if ("FAILED".equals(payload.getStatus())) {
                bookingService.cancelBooking(
                    payload.getBookingId(),
                    "Payment failed: " + payload.getReason()
                );
            }
            
            // 4. Mark webhook processed
            idempotencyService.markProcessed(idempotencyKey);
            
            return ResponseEntity.ok().build();
            
        } catch (Exception e) {
            log.error("Webhook processing failed", e);
            // Return 5xx to trigger retry from provider
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
```

### Confirmation Logic

```java
@Transactional
public void confirmBooking(UUID bookingId, UUID paymentId) {
    
    // 1. Load booking with lock
    Booking booking = bookingRepository
        .findByIdForUpdate(bookingId)
        .orElseThrow(() -> new BookingNotFoundException(bookingId));
    
    // 2. Validate current status
    if (booking.getStatus() != BookingStatus.PAYMENT_INITIATED) {
        log.warn("Booking {} not in PAYMENT_INITIATED state", bookingId);
        return;
    }
    
    // 3. Verify payment with Payment Service
    PaymentStatus paymentStatus = paymentService.getPaymentStatus(paymentId);
    if (paymentStatus != PaymentStatus.SUCCESS) {
        throw new PaymentNotSuccessfulException(paymentId);
    }
    
    // 4. Transition to PAID then CONFIRMED
    booking.setPaymentId(paymentId);
    booking.setPaymentStatus("SUCCESS");
    booking.transitionTo(BookingStatus.PAID, "Payment confirmed");
    booking.transitionTo(BookingStatus.CONFIRMED, "Booking confirmed");
    booking.setConfirmedAt(Instant.now());
    booking.setExpiresAt(null); // No longer expires
    
    bookingRepository.save(booking);
    
    // 5. Release Redis hold (no longer needed)
    seatHoldService.releaseSeats(
        booking.getRouteId(),
        booking.getTravelDate(),
        booking.getSeatNumbers(),
        booking.getId()
    );
    
    // 6. Update route seat count (cache invalidation)
    routeService.invalidateSeatCache(
        booking.getRouteId(),
        booking.getTravelDate()
    );
    
    // 7. Generate ticket
    ticketService.generateTicket(booking.getId());
    
    // 8. Send notification
    notificationService.sendBookingConfirmation(booking);
    
    // 9. Publish event
    publishBookingConfirmedEvent(booking);
}
```

---

## Cancellation & Refund Logic

### Cancellation Policy

```java
public enum CancellationPolicy {
    FULL_REFUND(Duration.ofHours(24), BigDecimal.ONE),        // 100% if >24h before
    PARTIAL_REFUND(Duration.ofHours(6), new BigDecimal("0.50")), // 50% if 6-24h before
    NO_REFUND(Duration.ZERO, BigDecimal.ZERO);                // 0% if <6h before
    
    private final Duration minimumNotice;
    private final BigDecimal refundPercentage;
}

public BigDecimal calculateRefund(Booking booking) {
    LocalDateTime departureTime = LocalDateTime.of(
        booking.getTravelDate(),
        booking.getDepartureTime()
    );
    
    Duration timeUntilDeparture = Duration.between(
        LocalDateTime.now(),
        departureTime
    );
    
    if (timeUntilDeparture.compareTo(CancellationPolicy.FULL_REFUND.minimumNotice) >= 0) {
        return booking.getTotalPrice(); // 100%
    } else if (timeUntilDeparture.compareTo(CancellationPolicy.PARTIAL_REFUND.minimumNotice) >= 0) {
        return booking.getTotalPrice().multiply(new BigDecimal("0.50")); // 50%
    } else {
        return BigDecimal.ZERO; // No refund
    }
}
```

### Cancellation Implementation

```java
@Transactional
public void cancelBooking(UUID bookingId, String reason) {
    
    // 1. Load booking with lock
    Booking booking = bookingRepository
        .findByIdForUpdate(bookingId)
        .orElseThrow(() -> new BookingNotFoundException(bookingId));
    
    // 2. Validate can cancel
    if (!booking.isCancellable()) {
        throw new BookingNotCancellableException(
            "Booking in status " + booking.getStatus() + " cannot be cancelled"
        );
    }
    
    // 3. Calculate refund amount
    BigDecimal refundAmount = calculateRefund(booking);
    
    // 4. Release seats
    if (booking.getStatus() == BookingStatus.HELD || 
        booking.getStatus() == BookingStatus.PAYMENT_INITIATED) {
        seatHoldService.releaseSeats(
            booking.getRouteId(),
            booking.getTravelDate(),
            booking.getSeatNumbers(),
            booking.getId()
        );
    }
    
    // 5. Update booking
    booking.transitionTo(BookingStatus.CANCELLED, reason);
    booking.setCancellationReason(reason);
    booking.setCancelledAt(Instant.now());
    booking.setRefundAmount(refundAmount);
    
    if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
        booking.setRefundStatus("PENDING");
    } else {
        booking.setRefundStatus("NONE");
    }
    
    bookingRepository.save(booking);
    
    // 6. Initiate refund if applicable
    if (refundAmount.compareTo(BigDecimal.ZERO) > 0 && booking.getPaymentId() != null) {
        paymentService.initiateRefund(
            booking.getPaymentId(),
            refundAmount,
            "Booking cancellation: " + reason
        );
    }
    
    // 7. Update route availability
    routeService.invalidateSeatCache(
        booking.getRouteId(),
        booking.getTravelDate()
    );
    
    // 8. Notify rider
    notificationService.sendCancellationNotification(booking, refundAmount);
    
    // 9. Publish event
    publishBookingCancelledEvent(booking);
}
```

---

## API Endpoints

### 1. Create Booking

**Request:**
```http
POST /v1/bookings
Content-Type: application/json
Authorization: Bearer {jwt_token}
X-Idempotency-Key: {unique_key}

{
  "route_id": "uuid",
  "origin_stop_id": "uuid",
  "destination_stop_id": "uuid",
  "travel_date": "2025-01-20",
  "seats_booked": 2
}
```

**Response:**
```json
{
  "booking": {
    "id": "uuid",
    "booking_reference": "BK20250113AB12",
    "route_id": "uuid",
    "route_name": "Lekki to VI Express",
    "origin_stop": {
      "id": "uuid",
      "name": "Lekki Phase 1",
      "coordinates": { "lat": 6.43, "lng": 3.51 }
    },
    "destination_stop": {
      "id": "uuid",
      "name": "Victoria Island",
      "coordinates": { "lat": 6.42, "lng": 3.42 }
    },
    "travel_date": "2025-01-20",
    "departure_time": "07:00:00",
    "seats_booked": 2,
    "seat_numbers": [1, 2],
    "total_price": 3000.00,
    "platform_fee": 150.00,
    "status": "PAYMENT_INITIATED",
    "expires_at": "2025-01-13T10:25:00Z",
    "created_at": "2025-01-13T10:15:00Z"
  },
  "payment_url": "https://payment.provider.com/checkout/xyz"
}
```

### 2. Get Booking Details

```http
GET /v1/bookings/{bookingId}
Authorization: Bearer {jwt_token}
```

### 3. List Bookings

```http
GET /v1/bookings?status=CONFIRMED&page=0&size=20
Authorization: Bearer {jwt_token}
```

### 4. Cancel Booking

```http
POST /v1/bookings/{bookingId}/cancel
Content-Type: application/json
Authorization: Bearer {jwt_token}

{
  "reason": "Changed plans"
}
```

### 5. Confirm Booking (Internal)

```http
POST /v1/bookings/{bookingId}/confirm
Content-Type: application/json
X-Internal-Service-Token: {service_token}

{
  "payment_id": "uuid",
  "payment_status": "SUCCESS"
}
```

---

## Performance Optimization

### Target Metrics
- **Mean Latency**: < 150ms for booking creation
- **P95 Latency**: < 300ms
- **P99 Latency**: < 500ms
- **Throughput**: > 100 concurrent bookings/second
- **Lock Wait Time**: < 100ms (p95)

### Optimization Strategies

1. **Database Indexes**
   - Composite index: (route_id, travel_date, status)
   - Partial index on expires_at for active bookings
   - Index on booking_reference for quick lookups

2. **Connection Pooling**
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 50
         minimum-idle: 10
         connection-timeout: 5000
   ```

3. **Redis Connection Pool**
   ```yaml
   spring:
     redis:
       lettuce:
         pool:
           max-active: 50
           max-idle: 10
           min-idle: 5
   ```

4. **Async Processing**
   - Ticket generation: async after confirmation
   - Notification sending: async via Kafka
   - Event publishing: fire-and-forget

5. **Caching**
   - Route details (5-minute TTL)
   - Seat availability (1-minute TTL, invalidated on booking)
   - Pricing matrix (15-minute TTL)

---

## Cleanup Jobs

### 1. Expired Seat Holds Cleanup

```java
@Scheduled(fixedRate = 300000) // Every 5 minutes
public void cleanupExpiredHolds() {
    
    Instant expiryThreshold = Instant.now();
    
    List<Booking> expiredBookings = bookingRepository
        .findByStatusInAndExpiresAtBefore(
            List.of(BookingStatus.PENDING, BookingStatus.HELD),
            expiryThreshold
        );
    
    for (Booking booking : expiredBookings) {
        try {
            // Release Redis holds
            seatHoldService.releaseSeats(
                booking.getRouteId(),
                booking.getTravelDate(),
                booking.getSeatNumbers(),
                booking.getId()
            );
            
            // Transition to EXPIRED
            booking.transitionTo(BookingStatus.EXPIRED, "Hold expired");
            bookingRepository.save(booking);
            
            log.info("Booking {} expired and cleaned up", booking.getId());
            
        } catch (Exception e) {
            log.error("Failed to cleanup booking {}", booking.getId(), e);
        }
    }
}
```

### 2. Orphaned Redis Holds Cleanup

```java
@Scheduled(cron = "0 */15 * * * *") // Every 15 minutes
public void cleanupOrphanedRedisHolds() {
    
    // Scan Redis for hold keys
    Set<String> holdKeys = redisTemplate.keys("seat:hold:*");
    
    for (String holdKey : holdKeys) {
        String bookingId = redisTemplate.opsForValue().get(holdKey);
        
        if (bookingId != null) {
            Optional<Booking> booking = bookingRepository
                .findById(UUID.fromString(bookingId));
            
            // If booking doesn't exist or is in terminal state, release hold
            if (booking.isEmpty() || booking.get().isTerminal()) {
                redisTemplate.delete(holdKey);
                log.info("Cleaned up orphaned hold: {}", holdKey);
            }
        }
    }
}
```

---

## Testing Strategy

### Unit Tests
- State machine transitions
- Refund calculation logic
- Seat allocation algorithm
- Distributed lock behavior
- Idempotency handling

### Integration Tests
- Full booking flow (create → pay → confirm)
- Concurrent booking attempts (race conditions)
- Payment webhook processing
- Cancellation with refund
- Seat hold expiration
- Redis failover scenarios

### Performance Tests
- 100 concurrent bookings for same route
- Lock contention under load
- Database connection pool exhaustion
- Redis connection pool exhaustion
- P95 latency validation

### Chaos Tests
- Redis temporary unavailability
- Database connection loss
- Payment service timeout
- Webhook delivery failures

---

## Implementation Checklist

- [ ] Database schema & migrations
- [ ] Redis configuration & Redisson setup
- [ ] Entity models (Booking, BookingStatusHistory)
- [ ] Repository layer with custom queries
- [ ] Seat hold service (Redis operations)
- [ ] Distributed lock service
- [ ] Booking state machine
- [ ] Seat availability service
- [ ] Booking service (create, confirm, cancel)
- [ ] Payment integration (initiate, webhook)
- [ ] Idempotency service
- [ ] REST controllers & DTOs
- [ ] Cleanup scheduled jobs
- [ ] Exception handling & error responses
- [ ] Unit tests
- [ ] Integration tests
- [ ] Performance tests
- [ ] API documentation
- [ ] Deployment configuration

---

## Next Steps

1. ✅ Create this implementation plan
2. 🚧 Set up project structure
3. ⏳ Implement database models & migrations
4. ⏳ Build seat hold mechanism
5. ⏳ Implement distributed locks
6. ⏳ Create booking service logic
7. ⏳ Add payment integration
8. ⏳ Build API endpoints
9. ⏳ Write comprehensive tests
10. ⏳ Performance testing & optimization
11. ⏳ Documentation

---

**Status**: Ready to implement 🚀 **CRITICAL PHASE**

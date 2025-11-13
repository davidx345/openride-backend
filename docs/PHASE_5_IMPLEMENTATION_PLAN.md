# Phase 5: Payment Processing with Korapay - Implementation Plan

## Executive Summary

**Service**: Payments Service (Java Spring Boot)  
**Payment Provider**: Korapay (Nigerian payment gateway)  
**Timeline**: Week 8-10  
**Criticality**: ⭐ **CRITICAL** - Revenue generation depends on this

---

## 1. Architecture Overview

### 1.1 Payment Flow

```
┌─────────┐         ┌──────────────┐         ┌─────────┐         ┌─────────────┐
│ Rider   │────────▶│   Payment    │────────▶│ Korapay │────────▶│   Booking   │
│ (App)   │         │   Service    │         │   API   │         │   Service   │
└─────────┘         └──────────────┘         └─────────┘         └─────────────┘
     │                      │                      │                      │
     │  1. Initiate         │  2. Create Payment   │                      │
     │  Payment             │     with Korapay     │                      │
     │◀─────────────────────│                      │                      │
     │  Return checkout URL │                      │                      │
     │                      │                      │                      │
     │  3. Redirect to      │                      │                      │
     │     Korapay          │                      │                      │
     │─────────────────────────────────────────────▶                      │
     │                      │                      │                      │
     │  4. Complete         │                      │                      │
     │     Payment          │                      │                      │
     │                      │◀─────────────────────│                      │
     │                      │  5. Webhook          │                      │
     │                      │     Notification     │                      │
     │                      │                      │                      │
     │                      │──────────────────────────────────────────────▶
     │                      │  6. Confirm Booking  │                      │
     │                      │                      │                      │
```

### 1.2 State Machine

```
INITIATED ──────▶ PENDING ──────▶ SUCCESS ──────▶ COMPLETED
                     │               │
                     │               │
                     ▼               ▼
                  FAILED         REFUNDED
                     │               │
                     └───────────────┘
                            │
                            ▼
                        TERMINAL
```

**State Definitions**:
- **INITIATED**: Payment request created, waiting for Korapay response
- **PENDING**: Payment sent to Korapay, waiting for user to complete
- **SUCCESS**: Payment confirmed by Korapay, booking confirmed
- **FAILED**: Payment failed (insufficient funds, cancelled, timeout)
- **REFUNDED**: Payment refunded to customer
- **COMPLETED**: Final state after successful payment processing

### 1.3 Korapay Integration Points

**Korapay API Endpoints**:
1. **Initialize Payment**: `POST /merchant/api/v1/charges/initialize`
2. **Verify Payment**: `GET /merchant/api/v1/charges/{reference}`
3. **Process Refund**: `POST /merchant/api/v1/refund/initialize`
4. **Query Transaction**: `GET /merchant/api/v1/charges/{reference}`
5. **Webhook**: Receives payment status updates

**Authentication**: Bearer token using Secret Key

---

## 2. Database Schema

### 2.1 Tables

```sql
-- Main payments table
CREATE TYPE payment_status AS ENUM (
    'INITIATED', 
    'PENDING', 
    'SUCCESS', 
    'FAILED', 
    'REFUNDED', 
    'COMPLETED'
);

CREATE TYPE payment_method AS ENUM (
    'CARD',
    'BANK_TRANSFER',
    'USSD',
    'MOBILE_MONEY'
);

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID NOT NULL,
    rider_id UUID NOT NULL,
    amount DECIMAL(10,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) DEFAULT 'NGN',
    status payment_status NOT NULL DEFAULT 'INITIATED',
    payment_method payment_method,
    
    -- Korapay fields
    korapay_reference VARCHAR(255) UNIQUE NOT NULL,
    korapay_transaction_id VARCHAR(255) UNIQUE,
    korapay_checkout_url TEXT,
    korapay_customer_email VARCHAR(255),
    korapay_customer_name VARCHAR(255),
    
    -- Metadata
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    failure_reason TEXT,
    refund_reason TEXT,
    refund_amount DECIMAL(10,2),
    refunded_at TIMESTAMP,
    
    -- Timestamps
    initiated_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP,
    expires_at TIMESTAMP,
    
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    CONSTRAINT fk_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT fk_rider FOREIGN KEY (rider_id) REFERENCES users(id)
);

-- Indexes for performance
CREATE INDEX idx_payments_booking ON payments(booking_id);
CREATE INDEX idx_payments_rider ON payments(rider_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_korapay_ref ON payments(korapay_reference);
CREATE INDEX idx_payments_idempotency ON payments(idempotency_key);
CREATE INDEX idx_payments_expires_at ON payments(expires_at) WHERE status = 'PENDING';

-- Payment events audit trail
CREATE TABLE payment_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    previous_status payment_status,
    new_status payment_status NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    
    CONSTRAINT fk_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_payment_events_payment ON payment_events(payment_id, created_at DESC);

-- Reconciliation records
CREATE TABLE reconciliation_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reconciliation_date DATE NOT NULL,
    total_payments_count INTEGER NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    korapay_count INTEGER NOT NULL,
    korapay_amount DECIMAL(12,2) NOT NULL,
    discrepancy_count INTEGER DEFAULT 0,
    discrepancies JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    
    UNIQUE(reconciliation_date)
);

CREATE INDEX idx_reconciliation_date ON reconciliation_records(reconciliation_date DESC);
```

---

## 3. Core Components

### 3.1 Payment State Machine

**Allowed Transitions**:
```
INITIATED → PENDING
PENDING → SUCCESS
PENDING → FAILED
SUCCESS → REFUNDED
SUCCESS → COMPLETED
```

**Guards**:
- Cannot transition to SUCCESS if Korapay verification fails
- Cannot refund if status is not SUCCESS
- Cannot retry if status is COMPLETED or REFUNDED

### 3.2 Idempotency Strategy

**Problem**: Prevent duplicate payments from retry requests or webhook replays

**Solution**:
1. **Request Idempotency**: Use Redis SET NX with idempotency_key
   - Key format: `payment:idempotency:{idempotency_key}`
   - TTL: 24 hours
   - Value: payment_id

2. **Webhook Idempotency**: Use Redis SET NX with korapay_reference + event_type
   - Key format: `webhook:processed:{korapay_reference}:{event_type}`
   - TTL: 7 days
   - Value: timestamp

### 3.3 Webhook Security

**Korapay Webhook Signature Verification**:
```
signature = HMAC-SHA256(webhook_secret, payload)
Compare with X-Korapay-Signature header
```

**Processing Steps**:
1. Verify signature
2. Check idempotency (Redis SET NX)
3. Validate payment exists
4. Update payment status
5. Log event
6. Trigger booking confirmation if SUCCESS
7. Return 200 OK

---

## 4. API Endpoints

### 4.1 Payment APIs

```
POST   /v1/payments/initiate
GET    /v1/payments/{paymentId}
GET    /v1/payments/booking/{bookingId}
POST   /v1/payments/{paymentId}/verify
POST   /v1/webhooks/korapay
GET    /v1/admin/payments
POST   /v1/admin/payments/{paymentId}/refund
GET    /v1/admin/reconciliation
POST   /v1/admin/reconciliation/run
```

### 4.2 Request/Response Examples

**Initiate Payment**:
```json
POST /v1/payments/initiate
{
  "bookingId": "uuid",
  "amount": 1500.00,
  "currency": "NGN",
  "customerEmail": "rider@example.com",
  "customerName": "John Doe",
  "idempotencyKey": "booking_uuid_timestamp"
}

Response 201:
{
  "paymentId": "uuid",
  "korapayReference": "KPY_REF_123",
  "checkoutUrl": "https://checkout.korapay.com/...",
  "amount": 1500.00,
  "status": "PENDING",
  "expiresAt": "2025-11-13T15:30:00Z"
}
```

**Webhook Payload**:
```json
POST /v1/webhooks/korapay
Headers:
  X-Korapay-Signature: sha256_signature

{
  "event": "charge.success",
  "data": {
    "reference": "KPY_REF_123",
    "amount": 1500.00,
    "currency": "NGN",
    "status": "success",
    "payment_method": "card",
    "customer": {
      "email": "rider@example.com"
    },
    "metadata": {
      "booking_id": "uuid"
    }
  }
}

Response 200:
{
  "status": "processed"
}
```

---

## 5. Configuration

### 5.1 Application Properties

```yaml
korapay:
  api-url: ${KORAPAY_API_URL:https://api.korapay.com}
  secret-key: ${KORAPAY_SECRET_KEY}
  public-key: ${KORAPAY_PUBLIC_KEY}
  webhook-secret: ${KORAPAY_WEBHOOK_SECRET}
  timeout-seconds: 30
  
payment:
  expiry-minutes: 15
  max-retry-attempts: 3
  retry-delay-seconds: 5
  
redis:
  idempotency-ttl-hours: 24
  webhook-ttl-days: 7
  
reconciliation:
  schedule: "0 0 2 * * ?" # Daily at 2 AM
  lookback-days: 7
  
booking-service:
  url: ${BOOKING_SERVICE_URL:http://localhost:8083}
  confirm-endpoint: /v1/bookings/{bookingId}/confirm
  timeout-seconds: 10
```

---

## 6. Testing Strategy

### 6.1 Unit Tests (80%+ Coverage)

- Payment state machine transitions
- Idempotency key validation
- Webhook signature verification
- Refund calculation
- Payment expiry logic

### 6.2 Integration Tests

- Full payment flow (initiate → webhook → confirm)
- Korapay API mocking
- Webhook replay attack prevention
- Concurrent payment attempts
- Reconciliation job execution

### 6.3 Test Scenarios

1. **Successful Payment Flow**:
   - Initiate → Pending → Success → Booking Confirmed

2. **Failed Payment**:
   - Initiate → Pending → Failed → Booking Cancelled

3. **Duplicate Request**:
   - Same idempotency_key returns existing payment

4. **Webhook Replay**:
   - Second webhook with same reference ignored

5. **Payment Expiry**:
   - Payment not completed within 15 minutes → FAILED

6. **Refund Flow**:
   - Success → Refund Request → Refunded

---

## 7. Deployment Checklist

- [ ] Korapay account created (production + test)
- [ ] Secret keys stored in Vault/Secrets Manager
- [ ] Database migrations executed
- [ ] Redis configured for idempotency
- [ ] Webhook URL whitelisted in Korapay dashboard
- [ ] Reconciliation job scheduled
- [ ] Monitoring alerts configured
- [ ] Payment logs sent to SIEM
- [ ] Load testing completed (1000+ payments/sec)
- [ ] Disaster recovery plan documented

---

## 8. Security Measures

1. **Webhook Verification**: HMAC-SHA256 signature validation
2. **Idempotency**: Redis-based deduplication
3. **Rate Limiting**: 100 payment requests per user per hour
4. **Audit Logging**: All payment events logged
5. **Encryption**: Sensitive data encrypted at rest
6. **Input Validation**: All requests validated with Bean Validation
7. **HTTPS Only**: All communication over TLS 1.3
8. **Secrets Management**: No hardcoded credentials

---

## 9. Performance Targets

- **Payment Initiation**: P95 < 150ms
- **Webhook Processing**: P95 < 100ms
- **Payment Verification**: P95 < 200ms
- **Throughput**: 1000+ payments/sec
- **Availability**: 99.95% uptime
- **Success Rate**: > 98%

---

## 10. Implementation Order

1. ✅ Architecture & Design (this document)
2. ⏳ Base structure & configuration
3. ⏳ Database schema & entities
4. ⏳ Payment state machine
5. ⏳ Korapay integration layer
6. ⏳ Core payment service
7. ⏳ Webhook handler
8. ⏳ REST API endpoints
9. ⏳ Reconciliation engine
10. ⏳ Booking service integration
11. ⏳ Comprehensive tests
12. ⏳ Documentation & scripts

---

## 11. Constraints Compliance

- ✅ Modular architecture (separate services, controllers, repositories)
- ✅ No file > 500 lines
- ✅ Clean code with dependency injection
- ✅ Google Java Style Guide
- ✅ Robust error handling for all external calls
- ✅ Input validation on all endpoints
- ✅ No secrets in code
- ✅ JWT authentication + authorization
- ✅ Rate limiting on public endpoints
- ✅ Optimized database queries with indexes
- ✅ Unit + integration + edge case tests
- ✅ Comprehensive documentation
- ✅ Full project review before finalization

---

**Status**: Ready for implementation 🚀

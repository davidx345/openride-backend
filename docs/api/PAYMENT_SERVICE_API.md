# Payment Service API Documentation

## Service Information
- **Service Name:** Payment Service
- **Port:** 8084
- **Base URL:** `/v1/payments`
- **Technology:** Java / Spring Boot
- **Description:** Handles payment processing, verification, and refunds using Korapay payment gateway

---

## Authentication

All endpoints require JWT authentication except webhooks.

**Request Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

---

## Payment Provider

This service integrates with **Korapay** for payment processing in Nigeria.

- **Currency:** NGN (Nigerian Naira)
- **Payment Methods:** Card, Bank Transfer, USSD
- **Settlement:** T+1 (Next business day)

---

## Endpoints

### 1. Initiate Payment

Initialize a payment for a booking.

**Endpoint:** `POST /v1/payments/initiate`

**Authentication:** Required

**Request Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "bookingId": "789e4567-e89b-12d3-a456-426614174004",
  "amount": 7000.00,
  "currency": "NGN",
  "customerEmail": "john.doe@example.com",
  "customerName": "John Doe",
  "idempotencyKey": "payment_1637490600_user123"
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| bookingId | UUID | Yes | Valid booking ID | Booking to pay for |
| amount | decimal | Yes | > 0, max 8 digits | Amount in NGN |
| currency | string | Yes | Must be "NGN" | Currency code (3 chars) |
| customerEmail | string | Yes | Valid email | Customer email for receipt |
| customerName | string | Yes | 2-100 chars | Customer name |
| idempotencyKey | string | Yes | 10-255 chars | Unique key to prevent duplicate charges |

**Success Response (200 OK):**
```json
{
  "id": "891e4567-e89b-12d3-a456-426614174005",
  "bookingId": "789e4567-e89b-12d3-a456-426614174004",
  "riderId": "123e4567-e89b-12d3-a456-426614174000",
  "amount": 7000.00,
  "currency": "NGN",
  "status": "PENDING",
  "paymentMethod": null,
  "korapayReference": "KPY_REF_1637490600",
  "korapayTransactionId": null,
  "korapayCheckoutUrl": "https://checkout.korapay.com/pay/KPY_REF_1637490600",
  "failureReason": null,
  "refundAmount": null,
  "refundedAt": null,
  "initiatedAt": "2025-11-21T10:30:00.000Z",
  "completedAt": null,
  "expiresAt": "2025-11-21T11:30:00.000Z",
  "createdAt": "2025-11-21T10:30:00.000Z"
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Payment unique identifier |
| bookingId | UUID | Associated booking ID |
| riderId | UUID | ID of user making payment |
| amount | decimal | Payment amount in NGN |
| currency | string | Currency code (NGN) |
| status | string | Payment status (see status flow) |
| paymentMethod | string | Payment method used (nullable until completed) |
| korapayReference | string | Korapay payment reference |
| korapayTransactionId | string | Korapay transaction ID (nullable until completed) |
| korapayCheckoutUrl | string | URL to redirect user for payment |
| failureReason | string | Reason for failure (nullable) |
| refundAmount | decimal | Refunded amount (nullable) |
| refundedAt | ISO 8601 | Refund timestamp (nullable) |
| initiatedAt | ISO 8601 | Payment initiation time |
| completedAt | ISO 8601 | Payment completion time (nullable) |
| expiresAt | ISO 8601 | Payment expiration time (1 hour) |
| createdAt | ISO 8601 | Record creation timestamp |

**Error Responses:**

**400 Bad Request:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Booking already paid",
  "path": "/v1/payments/initiate"
}
```

**404 Not Found:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "Booking not found",
  "path": "/v1/payments/initiate"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8084/v1/payments/initiate \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "bookingId": "789e4567-e89b-12d3-a456-426614174004",
    "amount": 7000.00,
    "currency": "NGN",
    "customerEmail": "john.doe@example.com",
    "customerName": "John Doe",
    "idempotencyKey": "payment_1637490600_user123"
  }'
```

---

### 2. Verify Payment

Verify payment status (called after user completes payment).

**Endpoint:** `POST /v1/payments/verify`

**Authentication:** Required

**Request Body:**
```json
{
  "reference": "KPY_REF_1637490600"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| reference | string | Yes | Korapay payment reference |

**Success Response (200 OK):**
```json
{
  "id": "891e4567-e89b-12d3-a456-426614174005",
  "bookingId": "789e4567-e89b-12d3-a456-426614174004",
  "riderId": "123e4567-e89b-12d3-a456-426614174000",
  "amount": 7000.00,
  "currency": "NGN",
  "status": "COMPLETED",
  "paymentMethod": "CARD",
  "korapayReference": "KPY_REF_1637490600",
  "korapayTransactionId": "TXN_1637490700",
  "korapayCheckoutUrl": "https://checkout.korapay.com/pay/KPY_REF_1637490600",
  "failureReason": null,
  "refundAmount": null,
  "refundedAt": null,
  "initiatedAt": "2025-11-21T10:30:00.000Z",
  "completedAt": "2025-11-21T10:32:00.000Z",
  "expiresAt": "2025-11-21T11:30:00.000Z",
  "createdAt": "2025-11-21T10:30:00.000Z"
}
```

**Error Responses:**

**400 Bad Request:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Payment verification failed",
  "path": "/v1/payments/verify"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8084/v1/payments/verify \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "reference": "KPY_REF_1637490600"
  }'
```

---

### 3. Get Payment by ID

Retrieve payment details.

**Endpoint:** `GET /v1/payments/{paymentId}`

**Authentication:** Required

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| paymentId | UUID | Yes | Payment unique identifier |

**Success Response (200 OK):**

Returns the same payment object structure as Initiate Payment response.

**Example cURL:**
```bash
curl -X GET http://localhost:8084/v1/payments/891e4567-e89b-12d3-a456-426614174005 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 4. List My Payments

Get all payments for authenticated user.

**Endpoint:** `GET /v1/payments/my-payments`

**Authentication:** Required

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| status | string | No | null | Filter by status |
| page | integer | No | 0 | Page number (0-indexed) |
| size | integer | No | 20 | Page size (max 100) |
| sort | string | No | createdAt,desc | Sort field and direction |

**Success Response (200 OK):**
```json
{
  "content": [
    {
      "id": "891e4567-e89b-12d3-a456-426614174005",
      "bookingId": "789e4567-e89b-12d3-a456-426614174004",
      "amount": 7000.00,
      "currency": "NGN",
      "status": "COMPLETED",
      "paymentMethod": "CARD",
      "initiatedAt": "2025-11-21T10:30:00.000Z",
      "completedAt": "2025-11-21T10:32:00.000Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "first": true
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8084/v1/payments/my-payments?status=COMPLETED&page=0&size=20" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 5. Payment Webhook (Korapay)

Webhook endpoint for Korapay to send payment notifications.

**Endpoint:** `POST /v1/payments/webhook`

**Authentication:** Not required (uses webhook signature verification)

**Request Headers:**
```
Content-Type: application/json
X-Korapay-Signature: <signature>
```

**Request Body (from Korapay):**
```json
{
  "event": "charge.success",
  "data": {
    "reference": "KPY_REF_1637490600",
    "transaction_reference": "TXN_1637490700",
    "amount": 7000.00,
    "currency": "NGN",
    "status": "success",
    "payment_method": "card",
    "customer": {
      "email": "john.doe@example.com",
      "name": "John Doe"
    },
    "created_at": "2025-11-21T10:32:00.000Z"
  }
}
```

**Success Response (200 OK):**
```json
{
  "status": "success",
  "message": "Webhook processed"
}
```

**Note:** This endpoint is for internal use by Korapay. Frontend does not call this directly.

---

## Payment Status Flow

```
PENDING → PROCESSING → COMPLETED
   ↓           ↓
FAILED     FAILED
   ↓
EXPIRED
```

**Status Descriptions:**

| Status | Description | Next Action |
|--------|-------------|-------------|
| PENDING | Payment initiated, awaiting user action | User completes payment |
| PROCESSING | Payment being processed by gateway | Wait for confirmation |
| COMPLETED | Payment successful | Booking confirmed |
| FAILED | Payment failed | Retry payment |
| EXPIRED | Payment link expired (1 hour) | Create new payment |
| REFUNDED | Payment refunded after cancellation | - |

---

## Payment Methods

Supported payment methods via Korapay:

| Method | Description | Processing Time |
|--------|-------------|-----------------|
| CARD | Debit/Credit card | Instant |
| BANK_TRANSFER | Direct bank transfer | 5-15 minutes |
| USSD | USSD code payment | 2-5 minutes |

---

## Data Models

### Payment Response Schema

```typescript
interface PaymentResponse {
  id: string;                           // UUID
  bookingId: string;                    // UUID
  riderId: string;                      // UUID
  amount: number;                       // Decimal (NGN)
  currency: string;                     // "NGN"
  status: PaymentStatus;
  paymentMethod: PaymentMethod | null;
  korapayReference: string;
  korapayTransactionId: string | null;
  korapayCheckoutUrl: string;
  failureReason: string | null;
  refundAmount: number | null;          // Decimal
  refundedAt: string | null;            // ISO 8601
  initiatedAt: string;                  // ISO 8601
  completedAt: string | null;           // ISO 8601
  expiresAt: string;                    // ISO 8601
  createdAt: string;                    // ISO 8601
}

type PaymentStatus = 
  | 'PENDING' 
  | 'PROCESSING' 
  | 'COMPLETED' 
  | 'FAILED' 
  | 'EXPIRED' 
  | 'REFUNDED';

type PaymentMethod = 
  | 'CARD' 
  | 'BANK_TRANSFER' 
  | 'USSD';
```

---

## Error Handling

### Common Error Responses

| Code | Error | Description |
|------|-------|-------------|
| 400 | Bad Request | Invalid request, duplicate payment, booking already paid |
| 401 | Unauthorized | Invalid or missing token |
| 404 | Not Found | Payment or booking not found |
| 409 | Conflict | Duplicate payment (idempotency check) |
| 500 | Internal Server Error | Payment gateway error |
| 502 | Bad Gateway | Korapay service unavailable |

---

## Integration Notes for Frontend

### Complete Payment Flow

```javascript
// 1. Initiate payment
async function initiatePayment(bookingId, amount, customerInfo) {
  const token = localStorage.getItem('accessToken');
  const idempotencyKey = `payment_${Date.now()}_${bookingId}`;
  
  const response = await fetch('http://localhost:8084/v1/payments/initiate', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      bookingId,
      amount,
      currency: 'NGN',
      customerEmail: customerInfo.email,
      customerName: customerInfo.name,
      idempotencyKey
    })
  });
  
  const payment = await response.json();
  
  // Store payment reference for verification
  sessionStorage.setItem('paymentReference', payment.korapayReference);
  
  return payment;
}

// 2. Open payment checkout
function openPaymentCheckout(checkoutUrl) {
  // Option 1: Redirect to payment page
  window.location.href = checkoutUrl;
  
  // Option 2: Open in popup
  const popup = window.open(
    checkoutUrl,
    'korapay_payment',
    'width=600,height=700'
  );
  
  // Listen for payment completion
  window.addEventListener('message', (event) => {
    if (event.data.type === 'payment_complete') {
      popup.close();
      verifyPayment(event.data.reference);
    }
  });
}

// 3. Verify payment (after user completes payment)
async function verifyPayment(reference) {
  const token = localStorage.getItem('accessToken');
  
  const response = await fetch('http://localhost:8084/v1/payments/verify', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ reference })
  });
  
  const payment = await response.json();
  
  if (payment.status === 'COMPLETED') {
    // Payment successful - show confirmation
    showPaymentSuccess(payment);
  } else if (payment.status === 'FAILED') {
    // Payment failed - show error
    showPaymentError(payment.failureReason);
  } else {
    // Still processing - poll for status
    pollPaymentStatus(payment.id);
  }
  
  return payment;
}

// 4. Poll payment status (for bank transfer/USSD)
async function pollPaymentStatus(paymentId) {
  const token = localStorage.getItem('accessToken');
  let attempts = 0;
  const maxAttempts = 60; // Poll for 5 minutes
  
  const interval = setInterval(async () => {
    attempts++;
    
    const response = await fetch(
      `http://localhost:8084/v1/payments/${paymentId}`,
      {
        headers: { 'Authorization': `Bearer ${token}` }
      }
    );
    
    const payment = await response.json();
    
    if (payment.status === 'COMPLETED') {
      clearInterval(interval);
      showPaymentSuccess(payment);
    } else if (payment.status === 'FAILED' || attempts >= maxAttempts) {
      clearInterval(interval);
      showPaymentError('Payment verification timeout');
    }
  }, 5000); // Poll every 5 seconds
}

// 5. Handle payment callback (after redirect back from Korapay)
function handlePaymentCallback() {
  const urlParams = new URLSearchParams(window.location.search);
  const reference = urlParams.get('reference');
  const status = urlParams.get('status');
  
  if (reference) {
    if (status === 'successful') {
      verifyPayment(reference);
    } else {
      showPaymentError('Payment was not completed');
    }
  }
}

// Call on payment success/callback page
window.addEventListener('load', handlePaymentCallback);
```

### Payment Status Monitoring

```javascript
// Real-time payment status updates
class PaymentMonitor {
  constructor(paymentId) {
    this.paymentId = paymentId;
    this.listeners = [];
  }
  
  async start() {
    this.interval = setInterval(async () => {
      const status = await this.checkStatus();
      this.notify(status);
      
      if (status.isFinal) {
        this.stop();
      }
    }, 3000);
  }
  
  async checkStatus() {
    const token = localStorage.getItem('accessToken');
    const response = await fetch(
      `http://localhost:8084/v1/payments/${this.paymentId}`,
      { headers: { 'Authorization': `Bearer ${token}` } }
    );
    const payment = await response.json();
    
    return {
      status: payment.status,
      isFinal: ['COMPLETED', 'FAILED', 'EXPIRED'].includes(payment.status),
      data: payment
    };
  }
  
  stop() {
    if (this.interval) {
      clearInterval(this.interval);
    }
  }
  
  onChange(callback) {
    this.listeners.push(callback);
  }
  
  notify(status) {
    this.listeners.forEach(listener => listener(status));
  }
}

// Usage
const monitor = new PaymentMonitor(paymentId);
monitor.onChange((status) => {
  console.log('Payment status:', status);
  updateUI(status);
});
monitor.start();
```

---

## Security Considerations

1. **Idempotency:**
   - Always use unique idempotency keys
   - Prevents duplicate charges if user clicks "Pay" multiple times

2. **Webhook Verification:**
   - All webhooks are verified using Korapay signature
   - Never trust webhook data without verification

3. **PCI Compliance:**
   - Card details never touch OpenRIDE servers
   - All sensitive data handled by Korapay

4. **Amount Validation:**
   - Backend always validates payment amount matches booking
   - Frontend amount is for display only

---

## Testing

### Test Cards (Korapay Sandbox)

| Card Number | CVV | Expiry | Result |
|-------------|-----|--------|--------|
| 5061 0201 5900 0009 | 123 | 12/25 | Success |
| 5061 0301 5900 0003 | 123 | 12/25 | Insufficient funds |
| 5061 0401 5900 0001 | 123 | 12/25 | Declined |

### Test Environment

- **Sandbox URL:** https://api.korapay.com/merchant/api/v1
- **Test Secret Key:** Available in Korapay dashboard
- **Webhook Testing:** Use ngrok for local webhook testing

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-21 | Initial API documentation |

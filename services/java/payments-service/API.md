# Payments Service API Documentation

## Base URL
```
Development: http://localhost:8085/api
Production: https://api.openride.com
```

## Authentication

All endpoints except webhooks require JWT authentication.

**Header:**
```
Authorization: Bearer <jwt_token>
```

---

## Rider Endpoints

### 1. Initiate Payment

Creates a new payment and returns Korapay checkout URL.

**Endpoint:** `POST /v1/payments/initiate`  
**Authentication:** Required (RIDER role)  
**Idempotent:** Yes (via idempotencyKey)

**Request Body:**
```json
{
  "bookingId": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 5000.00,
  "currency": "NGN",
  "customerEmail": "rider@example.com",
  "customerName": "John Doe",
  "idempotencyKey": "booking-550e8400-2024-01-15-12-30"
}
```

**Validation:**
- `bookingId`: Required, must be valid UUID
- `amount`: Required, minimum 0.01, maximum 8 digits + 2 decimals
- `currency`: Optional, default "NGN", must be 3 characters
- `customerEmail`: Required, valid email format
- `customerName`: Required, 2-100 characters
- `idempotencyKey`: Required, 10-255 characters, must be unique

**Success Response:** `201 Created`
```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "bookingId": "550e8400-e29b-41d4-a716-446655440000",
  "riderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "amount": 5000.00,
  "currency": "NGN",
  "status": "PENDING",
  "paymentMethod": null,
  "checkoutUrl": "https://korapay.com/checkout/ref_abc123xyz",
  "korapayReference": "OPENRIDE_550E8400E29B41D4_1704096000000",
  "initiatedAt": "2024-01-15T12:30:00",
  "expiresAt": "2024-01-15T12:45:00",
  "completedAt": null,
  "createdAt": "2024-01-15T12:30:00",
  "updatedAt": "2024-01-15T12:30:00"
}
```

**Error Responses:**

`400 Bad Request` - Validation error
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid request",
  "details": {
    "amount": "must be greater than 0.01",
    "customerEmail": "must be a well-formed email address"
  }
}
```

`409 Conflict` - Duplicate payment
```json
{
  "error": "DUPLICATE_PAYMENT",
  "message": "Payment already exists with idempotency key: booking-550e8400-2024-01-15-12-30"
}
```

`409 Conflict` - Booking already has payment
```json
{
  "error": "DUPLICATE_PAYMENT",
  "message": "Booking already has an associated payment: 550e8400-e29b-41d4-a716-446655440000"
}
```

---

### 2. Get Payment

Retrieves payment details by ID. Riders can only access their own payments.

**Endpoint:** `GET /v1/payments/{id}`  
**Authentication:** Required (RIDER/ADMIN role)

**Path Parameters:**
- `id` - Payment UUID

**Success Response:** `200 OK`
```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "bookingId": "550e8400-e29b-41d4-a716-446655440000",
  "riderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "amount": 5000.00,
  "currency": "NGN",
  "status": "SUCCESS",
  "paymentMethod": "CARD",
  "checkoutUrl": "https://korapay.com/checkout/ref_abc123xyz",
  "korapayReference": "OPENRIDE_550E8400E29B41D4_1704096000000",
  "initiatedAt": "2024-01-15T12:30:00",
  "expiresAt": "2024-01-15T12:45:00",
  "completedAt": "2024-01-15T12:32:15",
  "createdAt": "2024-01-15T12:30:00",
  "updatedAt": "2024-01-15T12:32:15"
}
```

**Error Responses:**

`403 Forbidden` - Not authorized
```json
{
  "error": "ACCESS_DENIED",
  "message": "You are not authorized to access this payment"
}
```

`404 Not Found` - Payment not found
```json
{
  "error": "PAYMENT_NOT_FOUND",
  "message": "Payment not found: 7c9e6679-7425-40de-944b-e07fc1f90ae7"
}
```

---

### 3. Get Payment by Booking

Retrieves payment for a specific booking.

**Endpoint:** `GET /v1/payments/booking/{bookingId}`  
**Authentication:** Required (RIDER/ADMIN role)

**Path Parameters:**
- `bookingId` - Booking UUID

**Success Response:** `200 OK`
```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "bookingId": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 5000.00,
  "status": "SUCCESS"
}
```

**Error Responses:**

`403 Forbidden` - Not authorized
`404 Not Found` - Payment not found for booking

---

### 4. List My Payments

Retrieves all payments for authenticated rider.

**Endpoint:** `GET /v1/payments/my-payments`  
**Authentication:** Required (RIDER role)

**Success Response:** `200 OK`
```json
[
  {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "bookingId": "550e8400-e29b-41d4-a716-446655440000",
    "amount": 5000.00,
    "status": "SUCCESS",
    "completedAt": "2024-01-15T12:32:15"
  },
  {
    "id": "8d0e7780-8536-51ef-c827-f08gd2g01bf8",
    "bookingId": "661f9511-f30c-52e5-b827-557766551111",
    "amount": 3500.00,
    "status": "PENDING",
    "expiresAt": "2024-01-14T15:45:00"
  }
]
```

---

### 5. Verify Payment

Manually verifies payment status with Korapay.

**Endpoint:** `POST /v1/payments/{id}/verify`  
**Authentication:** Required (RIDER/ADMIN role)

**Path Parameters:**
- `id` - Payment UUID

**Success Response:** `200 OK`
```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "status": "SUCCESS",
  "message": "Payment verified successfully"
}
```

**Error Responses:**

`403 Forbidden` - Not authorized
`404 Not Found` - Payment not found
`500 Internal Server Error` - Korapay API error

---

## Webhook Endpoints

### 6. Korapay Webhook

Receives payment notifications from Korapay.

**Endpoint:** `POST /v1/webhooks/korapay`  
**Authentication:** None (signature validation required)  
**Public:** Yes

**Headers:**
```
Content-Type: application/json
X-Korapay-Signature: <hmac-sha256-signature>
```

**Request Body:**
```json
{
  "event": "charge.success",
  "data": {
    "reference": "OPENRIDE_550E8400E29B41D4_1704096000000",
    "amount": 500000,
    "currency": "NGN",
    "status": "success",
    "transactionReference": "KPY-TXN-123456789",
    "paymentMethod": "card",
    "customer": {
      "name": "John Doe",
      "email": "rider@example.com"
    }
  }
}
```

**Event Types:**
- `charge.success` - Payment successful
- `charge.failed` - Payment failed

**Success Response:** `200 OK`
```json
{
  "status": "processed"
}
```

**Error Responses:**

`401 Unauthorized` - Invalid signature
```json
{
  "error": "INVALID_SIGNATURE",
  "message": "Webhook signature validation failed"
}
```

`500 Internal Server Error` - Processing failed
```json
{
  "error": "WEBHOOK_PROCESSING_FAILED",
  "message": "Failed to process webhook"
}
```

---

## Admin Endpoints

### 7. List All Payments

Lists all payments with optional filters.

**Endpoint:** `GET /v1/admin/payments`  
**Authentication:** Required (ADMIN role)

**Query Parameters:**
- `status` - Filter by payment status (INITIATED, PENDING, SUCCESS, FAILED, REFUNDED, COMPLETED)
- `riderId` - Filter by rider UUID

**Examples:**
```
GET /v1/admin/payments
GET /v1/admin/payments?status=SUCCESS
GET /v1/admin/payments?riderId=3fa85f64-5717-4562-b3fc-2c963f66afa6
GET /v1/admin/payments?status=FAILED&riderId=3fa85f64-5717-4562-b3fc-2c963f66afa6
```

**Success Response:** `200 OK`
```json
[
  {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "riderId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "amount": 5000.00,
    "status": "SUCCESS"
  }
]
```

---

### 8. Refund Payment

Processes a refund for a successful payment.

**Endpoint:** `POST /v1/admin/payments/{paymentId}/refund`  
**Authentication:** Required (ADMIN role)

**Path Parameters:**
- `paymentId` - Payment UUID

**Request Body:**
```json
{
  "amount": 2500.00,
  "reason": "Service not rendered as expected"
}
```

**Validation:**
- `amount`: Optional (null = full refund), must be ≤ payment amount
- `reason`: Required, 5-500 characters

**Success Response:** `200 OK`
```json
{
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "amount": 5000.00,
  "status": "REFUNDED",
  "refundAmount": 2500.00,
  "refundReason": "Service not rendered as expected",
  "refundedAt": "2024-01-15T14:30:00"
}
```

**Error Responses:**

`400 Bad Request` - Invalid refund
```json
{
  "error": "INVALID_REFUND",
  "message": "Payment is not in a refundable state"
}
```

`400 Bad Request` - Amount exceeds payment
```json
{
  "error": "INVALID_REFUND_AMOUNT",
  "message": "Refund amount cannot exceed payment amount"
}
```

---

### 9. Expire Payments

Manually runs payment expiration job.

**Endpoint:** `POST /v1/admin/payments/expire`  
**Authentication:** Required (ADMIN role)

**Success Response:** `200 OK`
```json
{
  "message": "Payment expiration completed",
  "expiredCount": 5
}
```

---

### 10. Run Reconciliation

Manually runs reconciliation for a specific date.

**Endpoint:** `POST /v1/admin/reconciliation/run`  
**Authentication:** Required (ADMIN role)

**Query Parameters:**
- `date` - Date to reconcile (ISO 8601 format, defaults to yesterday)

**Examples:**
```
POST /v1/admin/reconciliation/run
POST /v1/admin/reconciliation/run?date=2024-01-14
```

**Success Response:** `200 OK`
```json
{
  "id": "9e1f8891-9647-62fg-d938-g19he3h12cg9",
  "reconciliationDate": "2024-01-14",
  "totalLocalPayments": 150,
  "totalKorapayPayments": 148,
  "totalLocalAmount": 750000.00,
  "totalKorapayAmount": 740000.00,
  "discrepancyCount": 2,
  "status": "DISCREPANCY",
  "notes": "Local: 150 payments, 750000.00 total. Korapay: 148 payments, 740000.00 total. Found 2 discrepancies.",
  "discrepancies": "[{\"paymentId\":\"...\",\"type\":\"AMOUNT_MISMATCH\",\"localAmount\":5000.00,\"korapayAmount\":4900.00}]",
  "createdAt": "2024-01-15T14:00:00"
}
```

---

### 11. Get Reconciliation Records

Retrieves latest reconciliation records.

**Endpoint:** `GET /v1/admin/reconciliation`  
**Authentication:** Required (ADMIN role)

**Query Parameters:**
- `limit` - Number of records to retrieve (default 10)

**Example:**
```
GET /v1/admin/reconciliation?limit=20
```

**Success Response:** `200 OK`
```json
[
  {
    "id": "9e1f8891-9647-62fg-d938-g19he3h12cg9",
    "reconciliationDate": "2024-01-14",
    "totalLocalPayments": 150,
    "totalKorapayPayments": 150,
    "discrepancyCount": 0,
    "status": "MATCHED"
  }
]
```

---

### 12. Get Discrepancies

Retrieves all reconciliation records with discrepancies.

**Endpoint:** `GET /v1/admin/reconciliation/discrepancies`  
**Authentication:** Required (ADMIN role)

**Success Response:** `200 OK`
```json
[
  {
    "id": "9e1f8891-9647-62fg-d938-g19he3h12cg9",
    "reconciliationDate": "2024-01-14",
    "discrepancyCount": 2,
    "status": "DISCREPANCY",
    "discrepancies": "[{\"type\":\"AMOUNT_MISMATCH\",...}]"
  }
]
```

---

## Payment Statuses

| Status | Description |
|--------|-------------|
| `INITIATED` | Payment created, awaiting Korapay initialization |
| `PENDING` | Korapay checkout created, awaiting user payment |
| `SUCCESS` | Payment successful, booking confirmed |
| `FAILED` | Payment failed or expired |
| `REFUNDED` | Payment refunded (full or partial) |
| `COMPLETED` | Payment successfully completed (alias for SUCCESS) |

## Payment Methods

| Method | Description |
|--------|-------------|
| `CARD` | Credit/Debit card |
| `BANK_TRANSFER` | Bank transfer |
| `USSD` | USSD code |
| `MOBILE_MONEY` | Mobile money wallet |

## Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `INVALID_STATE_TRANSITION` | 400 | Invalid payment state transition |
| `INVALID_REFUND` | 400 | Payment not refundable |
| `ACCESS_DENIED` | 403 | Not authorized to access resource |
| `PAYMENT_NOT_FOUND` | 404 | Payment not found |
| `DUPLICATE_PAYMENT` | 409 | Duplicate idempotency key or booking |
| `PAYMENT_EXCEPTION` | 500 | General payment error |
| `WEBHOOK_PROCESSING_FAILED` | 500 | Webhook processing failed |
| `INVALID_SIGNATURE` | 401 | Invalid webhook signature |

---

## Rate Limits

- **Rider Endpoints**: 100 requests/minute per user
- **Admin Endpoints**: 1000 requests/minute
- **Webhook Endpoint**: 10,000 requests/minute

---

## Idempotency

### Payment Idempotency

Use the `idempotencyKey` field to prevent duplicate payments. The key should be unique per payment attempt.

**Recommended Format:**
```
booking-{bookingId}-{timestamp}
```

**Behavior:**
- First request: Creates payment, returns 201
- Duplicate request: Returns 409 with existing payment ID
- TTL: 24 hours

### Webhook Idempotency

Webhooks are automatically deduplicated using `{korapayReference}_{eventType}`.

**TTL**: 7 days

---

## Testing

### Test Mode

Set `KORAPAY_TEST_MODE=true` to use Korapay test environment.

### Test Cards

**Success:**
```
Card: 5123 4567 8901 2346
CVV: 100
Expiry: 12/25
```

**Failure:**
```
Card: 5123 4567 8901 2347
CVV: 200
Expiry: 12/25
```

---

## Support

**API Issues**: tech@openride.com  
**Korapay Support**: support@korapay.com  
**Documentation**: https://docs.korapay.com

---

**Version**: 1.0.0  
**Last Updated**: January 2025

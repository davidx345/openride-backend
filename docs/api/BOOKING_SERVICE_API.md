# Booking Service API Documentation

## Service Information
- **Service Name:** Booking Service
- **Port:** 8083
- **Base URL:** `/v1/bookings`
- **Technology:** Java / Spring Boot
- **Description:** Manages ride bookings, seat reservations, and booking lifecycle

---

## Authentication

All endpoints require JWT authentication.

**Request Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

---

## Endpoints

### 1. Create Booking

Create a new booking for a route.

**Endpoint:** `POST /v1/bookings`

**Authentication:** Required (RIDER role)

**Request Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

**Request Body:**
```json
{
  "routeId": "123e4567-e89b-12d3-a456-426614174000",
  "originStopId": "234e4567-e89b-12d3-a456-426614174001",
  "destinationStopId": "345e4567-e89b-12d3-a456-426614174002",
  "travelDate": "2025-11-25",
  "seatsBooked": 2,
  "idempotencyKey": "booking_1637490600_user123",
  "searchId": "456e4567-e89b-12d3-a456-426614174003",
  "candidateRank": 1,
  "candidateCount": 5
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| routeId | UUID | Yes | Valid UUID | Route to book |
| originStopId | UUID | Yes | Valid UUID | Pickup stop ID |
| destinationStopId | UUID | Yes | Valid UUID | Dropoff stop ID |
| travelDate | date | Yes | Must be future date | Date of travel (YYYY-MM-DD) |
| seatsBooked | integer | Yes | Min: 1, Max: 10 | Number of seats to book |
| idempotencyKey | string | No | Max 100 chars | Unique key to prevent duplicate bookings |
| searchId | UUID | No | Valid UUID | Search tracking ID |
| candidateRank | integer | No | Min: 1 | Rank of selected route in search results |
| candidateCount | integer | No | Min: 1 | Total candidates in search |

**Success Response (201 Created):**
```json
{
  "id": "789e4567-e89b-12d3-a456-426614174004",
  "bookingReference": "BK-20251121-ABC123",
  "riderId": "123e4567-e89b-12d3-a456-426614174000",
  "driverId": "234e4567-e89b-12d3-a456-426614174001",
  "routeId": "123e4567-e89b-12d3-a456-426614174000",
  "routeName": "Lagos - Ibadan Express",
  "originStop": {
    "id": "234e4567-e89b-12d3-a456-426614174001",
    "name": "Ojota Bus Stop",
    "coordinates": {
      "lat": 6.5833,
      "lng": 3.3833
    },
    "sequenceNumber": 1
  },
  "destinationStop": {
    "id": "345e4567-e89b-12d3-a456-426614174002",
    "name": "Challenge Bus Stop",
    "coordinates": {
      "lat": 7.3833,
      "lng": 3.9000
    },
    "sequenceNumber": 5
  },
  "travelDate": "2025-11-25",
  "departureTime": "08:00:00",
  "seatsBooked": 2,
  "seatNumbers": [5, 6],
  "pricePerSeat": 3500.00,
  "totalPrice": 7000.00,
  "platformFee": 350.00,
  "status": "PENDING_PAYMENT",
  "paymentId": null,
  "paymentStatus": "PENDING",
  "cancellationReason": null,
  "cancelledAt": null,
  "refundAmount": null,
  "refundStatus": null,
  "bookingSource": "MOBILE_APP",
  "createdAt": "2025-11-21T10:30:00.000Z",
  "updatedAt": "2025-11-21T10:30:00.000Z",
  "expiresAt": "2025-11-21T10:45:00.000Z",
  "confirmedAt": null
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Booking unique identifier |
| bookingReference | string | Human-readable booking reference |
| riderId | UUID | ID of rider who made booking |
| driverId | UUID | ID of driver for this route |
| routeId | UUID | ID of booked route |
| routeName | string | Name of the route |
| originStop | object | Pickup stop details |
| destinationStop | object | Dropoff stop details |
| travelDate | date | Date of travel |
| departureTime | time | Departure time (HH:MM:SS) |
| seatsBooked | integer | Number of seats booked |
| seatNumbers | array | Specific seat numbers allocated |
| pricePerSeat | decimal | Price per seat in NGN |
| totalPrice | decimal | Total booking price (seats × price) |
| platformFee | decimal | Platform service fee |
| status | string | Booking status (see status flow) |
| paymentId | UUID | Associated payment ID (nullable) |
| paymentStatus | string | Payment status |
| cancellationReason | string | Reason for cancellation (nullable) |
| cancelledAt | ISO 8601 | Cancellation timestamp (nullable) |
| refundAmount | decimal | Refund amount if cancelled (nullable) |
| refundStatus | string | Refund status (nullable) |
| bookingSource | string | Source of booking |
| createdAt | ISO 8601 | Booking creation timestamp |
| updatedAt | ISO 8601 | Last update timestamp |
| expiresAt | ISO 8601 | Payment expiration time (15 min) |
| confirmedAt | ISO 8601 | Confirmation timestamp (nullable) |

**Error Responses:**

**400 Bad Request:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Insufficient seats available",
  "path": "/v1/bookings"
}
```

**409 Conflict:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 409,
  "error": "Conflict",
  "message": "Duplicate booking detected (idempotency key already used)",
  "path": "/v1/bookings"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8083/v1/bookings \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "routeId": "123e4567-e89b-12d3-a456-426614174000",
    "originStopId": "234e4567-e89b-12d3-a456-426614174001",
    "destinationStopId": "345e4567-e89b-12d3-a456-426614174002",
    "travelDate": "2025-11-25",
    "seatsBooked": 2
  }'
```

---

### 2. Get Booking by ID

Retrieve specific booking details.

**Endpoint:** `GET /v1/bookings/{bookingId}`

**Authentication:** Required

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| bookingId | UUID | Yes | Booking unique identifier |

**Success Response (200 OK):**

Returns the same booking object structure as Create Booking response.

**Error Responses:**

**404 Not Found:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 404,
  "error": "Not Found",
  "message": "Booking not found",
  "path": "/v1/bookings/789e4567-e89b-12d3-a456-426614174004"
}
```

**Example cURL:**
```bash
curl -X GET http://localhost:8083/v1/bookings/789e4567-e89b-12d3-a456-426614174004 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 3. List My Bookings

Get all bookings for authenticated user.

**Endpoint:** `GET /v1/bookings`

**Authentication:** Required

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| status | string | No | null | Filter by status (PENDING_PAYMENT, CONFIRMED, CANCELLED) |
| page | integer | No | 0 | Page number (0-indexed) |
| size | integer | No | 20 | Page size (max 100) |
| sort | string | No | createdAt,desc | Sort field and direction |

**Success Response (200 OK):**
```json
{
  "content": [
    {
      "id": "789e4567-e89b-12d3-a456-426614174004",
      "bookingReference": "BK-20251121-ABC123",
      "routeName": "Lagos - Ibadan Express",
      "travelDate": "2025-11-25",
      "departureTime": "08:00:00",
      "status": "CONFIRMED",
      "totalPrice": 7000.00,
      "seatsBooked": 2,
      "createdAt": "2025-11-21T10:30:00.000Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "sorted": true,
      "unsorted": false,
      "empty": false
    },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalElements": 1,
  "totalPages": 1,
  "last": true,
  "size": 20,
  "number": 0,
  "sort": {
    "sorted": true,
    "unsorted": false,
    "empty": false
  },
  "numberOfElements": 1,
  "first": true,
  "empty": false
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8083/v1/bookings?status=CONFIRMED&page=0&size=20" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 4. Get Upcoming Bookings

Get user's upcoming bookings (future travel dates).

**Endpoint:** `GET /v1/bookings/upcoming`

**Authentication:** Required

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| limit | integer | No | 10 | Maximum results (max 50) |

**Success Response (200 OK):**
```json
[
  {
    "id": "789e4567-e89b-12d3-a456-426614174004",
    "bookingReference": "BK-20251121-ABC123",
    "riderId": "123e4567-e89b-12d3-a456-426614174000",
    "driverId": "234e4567-e89b-12d3-a456-426614174001",
    "routeId": "123e4567-e89b-12d3-a456-426614174000",
    "routeName": "Lagos - Ibadan Express",
    "originStop": {
      "id": "234e4567-e89b-12d3-a456-426614174001",
      "name": "Ojota Bus Stop",
      "coordinates": {
        "lat": 6.5833,
        "lng": 3.3833
      },
      "sequenceNumber": 1
    },
    "destinationStop": {
      "id": "345e4567-e89b-12d3-a456-426614174002",
      "name": "Challenge Bus Stop",
      "coordinates": {
        "lat": 7.3833,
        "lng": 3.9000
      },
      "sequenceNumber": 5
    },
    "travelDate": "2025-11-25",
    "departureTime": "08:00:00",
    "seatsBooked": 2,
    "seatNumbers": [5, 6],
    "totalPrice": 7000.00,
    "status": "CONFIRMED",
    "paymentStatus": "COMPLETED"
  }
]
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8083/v1/bookings/upcoming?limit=10" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 5. Cancel Booking

Cancel an existing booking.

**Endpoint:** `POST /v1/bookings/{bookingId}/cancel`

**Authentication:** Required

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| bookingId | UUID | Yes | Booking to cancel |

**Request Body:**
```json
{
  "reason": "Change of plans"
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| reason | string | Yes | Max 500 chars | Reason for cancellation |

**Success Response (200 OK):**
```json
{
  "id": "789e4567-e89b-12d3-a456-426614174004",
  "bookingReference": "BK-20251121-ABC123",
  "status": "CANCELLED",
  "cancellationReason": "Change of plans",
  "cancelledAt": "2025-11-21T11:00:00.000Z",
  "refundAmount": 6650.00,
  "refundStatus": "PENDING",
  "totalPrice": 7000.00,
  "platformFee": 350.00
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| refundAmount | decimal | Amount to be refunded (price minus cancellation fee) |
| refundStatus | string | PENDING, PROCESSING, COMPLETED, FAILED |

**Cancellation Policy:**

| Time Before Departure | Refund Percentage |
|-----------------------|-------------------|
| > 24 hours | 95% (5% cancellation fee) |
| 12-24 hours | 75% (25% cancellation fee) |
| 6-12 hours | 50% (50% cancellation fee) |
| < 6 hours | 0% (no refund) |

**Error Responses:**

**400 Bad Request:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Cannot cancel booking: already cancelled",
  "path": "/v1/bookings/789e4567-e89b-12d3-a456-426614174004/cancel"
}
```

**403 Forbidden:**
```json
{
  "timestamp": "2025-11-21T10:30:00.000Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Cannot cancel booking: trip already started",
  "path": "/v1/bookings/789e4567-e89b-12d3-a456-426614174004/cancel"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8083/v1/bookings/789e4567-e89b-12d3-a456-426614174004/cancel \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "Change of plans"
  }'
```

---

## Booking Status Flow

```
PENDING_PAYMENT → CONFIRMED → COMPLETED
       ↓              ↓           
   CANCELLED     CANCELLED    
       ↓
   EXPIRED
```

**Status Descriptions:**

| Status | Description | Actions Available |
|--------|-------------|-------------------|
| PENDING_PAYMENT | Booking created, awaiting payment | Pay, Cancel |
| CONFIRMED | Payment completed, booking confirmed | Cancel, View ticket |
| CANCELLED | Booking cancelled by user or system | View refund status |
| EXPIRED | Payment not completed within 15 minutes | None (auto-cancelled) |
| COMPLETED | Trip completed successfully | Rate driver |

---

## Payment Status

| Status | Description |
|--------|-------------|
| PENDING | Awaiting payment initiation |
| PROCESSING | Payment in progress |
| COMPLETED | Payment successful |
| FAILED | Payment failed |
| REFUNDED | Payment refunded after cancellation |

---

## Refund Status

| Status | Description | Timeline |
|--------|-------------|----------|
| PENDING | Refund request created | Immediate |
| PROCESSING | Refund being processed | 1-2 hours |
| COMPLETED | Refund sent to user | 3-5 business days |
| FAILED | Refund failed (manual intervention needed) | Contact support |

---

## Data Models

### Booking Response Schema

```typescript
interface BookingResponse {
  id: string;                           // UUID
  bookingReference: string;             // BK-YYYYMMDD-XXXXXX
  riderId: string;                      // UUID
  driverId: string;                     // UUID
  routeId: string;                      // UUID
  routeName: string;
  originStop: StopInfo;
  destinationStop: StopInfo;
  travelDate: string;                   // YYYY-MM-DD
  departureTime: string;                // HH:MM:SS
  seatsBooked: number;
  seatNumbers: number[];
  pricePerSeat: number;                 // Decimal
  totalPrice: number;                   // Decimal
  platformFee: number;                  // Decimal
  status: BookingStatus;
  paymentId: string | null;             // UUID
  paymentStatus: PaymentStatus;
  cancellationReason: string | null;
  cancelledAt: string | null;           // ISO 8601
  refundAmount: number | null;          // Decimal
  refundStatus: RefundStatus | null;
  bookingSource: BookingSource;
  createdAt: string;                    // ISO 8601
  updatedAt: string;                    // ISO 8601
  expiresAt: string;                    // ISO 8601
  confirmedAt: string | null;           // ISO 8601
}

interface StopInfo {
  id: string;                           // UUID
  name: string;
  coordinates: {
    lat: number;
    lng: number;
  };
  sequenceNumber: number;
}

type BookingStatus = 
  | 'PENDING_PAYMENT' 
  | 'CONFIRMED' 
  | 'CANCELLED' 
  | 'EXPIRED' 
  | 'COMPLETED';

type PaymentStatus = 
  | 'PENDING' 
  | 'PROCESSING' 
  | 'COMPLETED' 
  | 'FAILED' 
  | 'REFUNDED';

type RefundStatus = 
  | 'PENDING' 
  | 'PROCESSING' 
  | 'COMPLETED' 
  | 'FAILED';

type BookingSource = 
  | 'MOBILE_APP' 
  | 'WEB' 
  | 'API';
```

---

## Error Handling

### Common Error Responses

| Code | Error | Description |
|------|-------|-------------|
| 400 | Bad Request | Invalid request data, insufficient seats, etc. |
| 401 | Unauthorized | Invalid or missing token |
| 403 | Forbidden | Cannot perform action (e.g., cancel completed trip) |
| 404 | Not Found | Booking not found |
| 409 | Conflict | Duplicate booking (idempotency check) |
| 500 | Internal Server Error | Server-side error |

---

## Integration Notes for Frontend

### Complete Booking Flow

```javascript
// 1. Create booking
async function createBooking(bookingData) {
  const token = localStorage.getItem('accessToken');
  const idempotencyKey = `booking_${Date.now()}_${userId}`;
  
  const response = await fetch('http://localhost:8083/v1/bookings', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      ...bookingData,
      idempotencyKey
    })
  });
  
  const booking = await response.json();
  
  // Store booking ID for payment
  sessionStorage.setItem('pendingBookingId', booking.id);
  
  return booking;
}

// 2. Check if payment is required
function requiresPayment(booking) {
  return booking.status === 'PENDING_PAYMENT';
}

// 3. Get upcoming bookings
async function getUpcomingBookings() {
  const token = localStorage.getItem('accessToken');
  const response = await fetch('http://localhost:8083/v1/bookings/upcoming', {
    headers: { 'Authorization': `Bearer ${token}` }
  });
  return response.json();
}

// 4. Cancel booking
async function cancelBooking(bookingId, reason) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(
    `http://localhost:8083/v1/bookings/${bookingId}/cancel`,
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ reason })
    }
  );
  return response.json();
}

// 5. Calculate refund amount
function calculateRefund(booking, cancellationTime) {
  const departureTime = new Date(`${booking.travelDate}T${booking.departureTime}`);
  const hoursUntilDeparture = (departureTime - cancellationTime) / (1000 * 60 * 60);
  
  let refundPercentage;
  if (hoursUntilDeparture > 24) refundPercentage = 0.95;
  else if (hoursUntilDeparture > 12) refundPercentage = 0.75;
  else if (hoursUntilDeparture > 6) refundPercentage = 0.50;
  else refundPercentage = 0;
  
  return booking.totalPrice * refundPercentage;
}
```

### Booking Expiration Timer

```javascript
// Show countdown timer for payment
function startExpirationTimer(expiresAt, onExpire) {
  const expirationTime = new Date(expiresAt);
  
  const timer = setInterval(() => {
    const now = new Date();
    const timeLeft = expirationTime - now;
    
    if (timeLeft <= 0) {
      clearInterval(timer);
      onExpire();
      return;
    }
    
    const minutes = Math.floor(timeLeft / 60000);
    const seconds = Math.floor((timeLeft % 60000) / 1000);
    
    // Update UI with remaining time
    updateTimerDisplay(`${minutes}:${seconds.toString().padStart(2, '0')}`);
  }, 1000);
  
  return timer;
}
```

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-21 | Initial API documentation |

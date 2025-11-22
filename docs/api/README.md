# OpenRIDE API Documentation Index

Complete API reference for all OpenRIDE microservices.

## Overview

This directory contains comprehensive API documentation for all 12 microservices that power the OpenRIDE platform. Each service documentation includes:

- Complete endpoint specifications
- Request/response schemas with field descriptions
- Authentication requirements
- Error handling
- Rate limiting
- Integration examples (cURL, JavaScript)
- TypeScript type definitions
- WebSocket documentation (where applicable)

---

## Services

### Core Services (Java/Spring Boot)

#### 1. [Auth Service](./AUTH_SERVICE_API.md) - Port 8081
**OTP-based authentication and token management**

Key Endpoints:
- `POST /auth/send-otp` - Send OTP
- `POST /auth/verify-otp` - Verify OTP and login
- `POST /auth/refresh-token` - Refresh access token
- `POST /auth/logout` - Logout

**Use Cases:** User registration, login, token refresh, logout

---

#### 2. [User Service](./USER_SERVICE_API.md) - Port 8082
**User profile management and KYC verification**

Key Endpoints:
- `POST /users` - Create user profile
- `GET /users/{userId}` - Get user profile
- `PUT /users/{userId}` - Update profile
- `POST /users/{userId}/upgrade-to-driver` - Upgrade to driver
- `POST /users/{userId}/kyc-documents` - Upload KYC documents

**Use Cases:** Profile management, role upgrades, KYC submission

---

#### 3. [Booking Service](./BOOKING_SERVICE_API.md) - Port 8083
**Booking lifecycle management**

Key Endpoints:
- `POST /bookings` - Create booking
- `GET /bookings/{bookingId}` - Get booking details
- `GET /bookings` - List user bookings
- `GET /bookings/upcoming` - Get upcoming bookings
- `DELETE /bookings/{bookingId}` - Cancel booking

**Use Cases:** Create reservations, view bookings, cancellations

---

#### 4. [Payment Service](./PAYMENT_SERVICE_API.md) - Port 8084
**Payment processing via Korapay (Nigeria)**

Key Endpoints:
- `POST /payments/initiate` - Initiate payment
- `GET /payments/verify/{reference}` - Verify payment
- `GET /payments/{paymentId}` - Get payment details
- `GET /payments` - List payments
- `POST /payments/webhook` - Korapay webhook

**Use Cases:** Payment processing, verification, transaction history

---

#### 5. [Ticketing Service](./TICKETING_SERVICE_API.md) - Port 8086
**Cryptographic tickets with blockchain anchoring (Polygon)**

Key Endpoints:
- `POST /tickets/generate` - Generate encrypted ticket
- `POST /tickets/verify` - Verify ticket authenticity
- `POST /tickets/{ticketId}/revoke` - Revoke ticket
- `GET /tickets/public-key` - Get public key for verification

**Use Cases:** Ticket generation, QR code verification, fraud prevention

---

#### 6. [Payouts Service](./PAYOUTS_SERVICE_API.md) - Port 8087
**Driver earnings and settlement management**

Key Endpoints:
- `POST /payouts/request` - Request payout
- `GET /payouts/requests` - List payout requests
- `GET /payouts/balance` - Get wallet balance
- `GET /payouts/earnings` - Get earnings summary
- `GET /payouts/ledger` - Get transaction ledger
- `POST /payouts/bank-accounts` - Add bank account

**Use Cases:** Driver withdrawals, earnings tracking, settlement processing

---

### Python Services (FastAPI)

#### 7. [Driver Service](./DRIVER_SERVICE_API.md) - Port 8091
**Route creation and management**

Key Endpoints:
- `POST /routes` - Create route with stops
- `GET /routes` - List driver routes
- `GET /routes/active` - Get active routes
- `GET /routes/{routeId}` - Get route details
- `PUT /routes/{routeId}` - Update route
- `PATCH /routes/{routeId}/status` - Update status
- `DELETE /routes/{routeId}` - Delete route

**Use Cases:** Route management, stop configuration, schedule setup

---

#### 8. [Matchmaking Service](./MATCHMAKING_SERVICE_API.md) - Port 8092
**Intelligent route matching algorithm**

Key Endpoints:
- `POST /match` - Find matching routes
- `GET /match/{routeId}` - Get match details
- `POST /match/score` - Calculate match score

**Features:**
- Weighted scoring algorithm (proximity, time, price, rating, availability)
- Geospatial calculations
- Time preference matching
- Price optimization

**Use Cases:** Route discovery, smart matching, ride recommendations

---

#### 9. [Search Service](./SEARCH_SERVICE_API.md) - Port 8093
**Public route discovery and search**

Key Endpoints:
- `POST /search` - Search routes (PUBLIC)
- `GET /routes/{routeId}` - Get route details (PUBLIC)
- `GET /routes/popular` - Get popular routes (PUBLIC)
- `GET /routes/nearby` - Find nearby routes (PUBLIC)
- `GET /locations/autocomplete` - Location suggestions (PUBLIC)
- `GET /routes/{routeId}/schedule` - Get route schedule (PUBLIC)
- `GET /routes/{routeId}/stats` - Get route statistics (PUBLIC)

**Use Cases:** Route browsing, location search, schedule viewing

---

#### 10. [Fleet Service](./FLEET_SERVICE_API.md) - Port 8096
**Real-time trip tracking and fleet management**

Key Endpoints:
- `POST /trips` - Create trip instance
- `POST /trips/{tripId}/start` - Start trip
- `POST /trips/{tripId}/location` - Update location
- `POST /trips/{tripId}/complete` - Complete trip
- `POST /trips/{tripId}/cancel` - Cancel trip
- `GET /trips/{tripId}` - Get trip details
- `GET /trips` - List driver trips
- `GET /trips/{tripId}/track` - Track trip (rider)

**WebSocket:** `ws://localhost:8096/ws/trips/{tripId}` - Real-time location updates

**Use Cases:** Trip tracking, live location updates, fleet monitoring

---

#### 11. [Analytics Service](./ANALYTICS_SERVICE_API.md) - Port 8097
**Business intelligence and metrics (ClickHouse)**

Key Endpoints:
- `GET /analytics/driver/{driverId}` - Driver analytics
- `GET /analytics/platform` - Platform metrics (ADMIN)
- `GET /analytics/route/{routeId}` - Route analytics
- `GET /analytics/bookings` - Booking metrics (ADMIN)
- `GET /analytics/revenue` - Revenue analytics (ADMIN)
- `GET /analytics/users` - User analytics (ADMIN)
- `POST /analytics/reports/custom` - Generate custom report (ADMIN)
- `POST /analytics/export` - Export analytics data

**Use Cases:** Performance dashboards, earnings reports, platform insights

---

#### 12. [Notification Service](./NOTIFICATION_SERVICE_API.md) - Port 8095
**Multi-channel notifications (push, SMS, email, in-app)**

Key Endpoints:
- `POST /notifications/send` - Send notification
- `POST /notifications/send-bulk` - Send bulk notifications (ADMIN)
- `GET /notifications/{notificationId}` - Get notification status
- `GET /notifications` - List user notifications
- `PATCH /notifications/{notificationId}/read` - Mark as read
- `PATCH /notifications/read-all` - Mark all as read
- `DELETE /notifications/{notificationId}` - Delete notification
- `POST /notifications/devices` - Register device token
- `DELETE /notifications/devices/{deviceId}` - Unregister device
- `PUT /notifications/preferences` - Update preferences
- `GET /notifications/preferences` - Get preferences

**WebSocket:** `ws://localhost:8095/ws/notifications` - Real-time notifications

**Use Cases:** Push notifications, SMS alerts, email notifications, preference management

---

## Service Architecture

### Technology Stack

**Java Services (Spring Boot 3.2):**
- Auth Service
- User Service
- Booking Service
- Payment Service
- Ticketing Service
- Payouts Service

**Python Services (FastAPI):**
- Driver Service
- Matchmaking Service
- Search Service
- Fleet Service
- Analytics Service
- Notification Service

### Data Stores

- **PostgreSQL** - Primary relational database for all services
- **ClickHouse** - Analytics data warehouse (Analytics Service)
- **Redis** - Caching and session management
- **Polygon Blockchain** - Ticket anchoring (Ticketing Service)

### External Integrations

- **Korapay** - Payment gateway (Nigeria)
- **Firebase Cloud Messaging (FCM)** - Push notifications
- **SMS Provider** - SMS notifications
- **Email Provider** - Email notifications

---

## Authentication

All services use JWT-based authentication (except public endpoints in Search Service).

**Header Format:**
```
Authorization: Bearer <access_token>
```

**Token Lifecycle:**
1. Get OTP via Auth Service → `POST /auth/send-otp`
2. Verify OTP and receive tokens → `POST /auth/verify-otp`
3. Use `access_token` for API calls
4. Refresh when expired → `POST /auth/refresh-token`

**Token Expiry:**
- Access Token: 15 minutes
- Refresh Token: 7 days

---

## Common Patterns

### Error Response Format

All services return errors in this format:

```json
{
  "detail": "Error message describing what went wrong"
}
```

### HTTP Status Codes

| Code | Meaning | When Used |
|------|---------|-----------|
| 200 | OK | Successful GET, PUT, PATCH |
| 201 | Created | Successful POST (resource created) |
| 204 | No Content | Successful DELETE |
| 400 | Bad Request | Invalid input, validation errors |
| 401 | Unauthorized | Missing or invalid token |
| 403 | Forbidden | Valid token but insufficient permissions |
| 404 | Not Found | Resource not found |
| 422 | Unprocessable Entity | Validation failed |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |

### Pagination

Services that return lists support pagination:

**Query Parameters:**
- `page` - Page number (default: 1)
- `page_size` - Results per page (default: 20, max: 100)

**Response:**
```json
{
  "results": [...],
  "pagination": {
    "page": 1,
    "page_size": 20,
    "total_results": 100,
    "total_pages": 5
  }
}
```

### Date/Time Formats

- **Dates:** `YYYY-MM-DD` (ISO 8601)
- **Times:** `HH:MM:SS` (24-hour format)
- **Datetimes:** `YYYY-MM-DDTHH:MM:SSZ` (ISO 8601 UTC)

### Field Naming Conventions

- **Java Services:** `camelCase`
- **Python Services:** `snake_case`

---

## Rate Limits

### Default Limits

| Category | Limit | Window |
|----------|-------|--------|
| Authentication | 10 requests | Per minute |
| Read Operations | 100 requests | Per minute |
| Write Operations | 20 requests | Per minute |
| WebSocket Connections | 5 connections | Per user |

### Service-Specific Limits

See individual service documentation for detailed rate limits.

---

## WebSocket Services

### Real-Time Features

**Fleet Service - Trip Tracking:**
- Endpoint: `ws://localhost:8096/ws/trips/{trip_id}`
- Updates: Location, status, stop arrivals
- Use: Live trip tracking for drivers and riders

**Notification Service - Real-Time Notifications:**
- Endpoint: `ws://localhost:8095/ws/notifications`
- Updates: All user notifications
- Use: In-app notification delivery

**Authentication:**
```
ws://service:port/endpoint?token=<access_token>
```

---

## Integration Flow Examples

### Complete Booking Flow

1. **Search for routes** → Search Service: `POST /search`
2. **Get match recommendations** → Matchmaking Service: `POST /match`
3. **Create booking** → Booking Service: `POST /bookings`
4. **Process payment** → Payment Service: `POST /payments/initiate`
5. **Verify payment** → Payment Service: `GET /payments/verify/{reference}`
6. **Generate ticket** → Ticketing Service: `POST /tickets/generate`
7. **Send confirmation** → Notification Service: `POST /notifications/send`

### Driver Onboarding Flow

1. **Register user** → Auth Service: `POST /auth/verify-otp`
2. **Create profile** → User Service: `POST /users`
3. **Upgrade to driver** → User Service: `POST /users/{userId}/upgrade-to-driver`
4. **Upload KYC docs** → User Service: `POST /users/{userId}/kyc-documents`
5. **Create first route** → Driver Service: `POST /routes`
6. **Route goes live** → Search Service (automatic indexing)

### Trip Execution Flow

1. **Create trip instance** → Fleet Service: `POST /trips`
2. **Start trip** → Fleet Service: `POST /trips/{tripId}/start`
3. **Send location updates** → Fleet Service: `POST /trips/{tripId}/location` (periodic)
4. **Riders track via WebSocket** → Fleet Service: `ws://localhost:8096/ws/trips/{tripId}`
5. **Complete trip** → Fleet Service: `POST /trips/{tripId}/complete`
6. **Process driver payout** → Payouts Service: `POST /payouts/request`

---

## Testing

### Base URLs

**Local Development:**
```
http://localhost:8081  # Auth Service
http://localhost:8082  # User Service
http://localhost:8083  # Booking Service
http://localhost:8084  # Payment Service
http://localhost:8086  # Ticketing Service
http://localhost:8087  # Payouts Service
http://localhost:8091  # Driver Service
http://localhost:8092  # Matchmaking Service
http://localhost:8093  # Search Service
http://localhost:8095  # Notification Service
http://localhost:8096  # Fleet Service
http://localhost:8097  # Analytics Service
```

### Sample Test Credentials

See individual service documentation for test data and examples.

---

## Support

For questions or issues with the API:

1. Check the specific service documentation
2. Review error messages and status codes
3. Verify authentication tokens
4. Check rate limits
5. Review request/response examples

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-21 | Initial comprehensive API documentation for all 12 services |

---

## Quick Links

### Java Services
- [Auth Service](./AUTH_SERVICE_API.md)
- [User Service](./USER_SERVICE_API.md)
- [Booking Service](./BOOKING_SERVICE_API.md)
- [Payment Service](./PAYMENT_SERVICE_API.md)
- [Ticketing Service](./TICKETING_SERVICE_API.md)
- [Payouts Service](./PAYOUTS_SERVICE_API.md)

### Python Services
- [Driver Service](./DRIVER_SERVICE_API.md)
- [Matchmaking Service](./MATCHMAKING_SERVICE_API.md)
- [Search Service](./SEARCH_SERVICE_API.md)
- [Fleet Service](./FLEET_SERVICE_API.md)
- [Analytics Service](./ANALYTICS_SERVICE_API.md)
- [Notification Service](./NOTIFICATION_SERVICE_API.md)

# OpenRIDE API Reference

This document provides a comprehensive reference for the OpenRIDE Backend API. It is intended for frontend developers and external integrators.

## Global Conventions

### Base URLs
All services are exposed via the API Gateway (or directly during development).
- **Development Base URL:** `http://localhost:<PORT>`
- **Production Base URL:** `https://api.openride.com`

### Authentication
- **Header:** `Authorization: Bearer <JWT_TOKEN>`
- **Token Type:** JWT (JSON Web Token)

### Data Formats
- **Request Body:** `application/json`
- **Response Body:** `application/json`
- **Date/Time:** ISO 8601 format (e.g., `2023-10-27T10:00:00Z`)

### Field Naming Conventions (CRITICAL)
Please note that the backend services use different casing conventions based on the implementation language. **Frontend must handle both.**

| Service Type | Language | JSON Field Casing | Example |
|--------------|----------|-------------------|---------|
| **Core Services** | Java (Spring Boot) | **camelCase** | `firstName`, `bookingId` |
| **Data/AI Services** | Python (FastAPI) | **snake_case** | `first_name`, `booking_id` |

---

## Java Services (camelCase)

### 1. Auth Service
**Port:** 8081
**Base Path:** `/v1/auth`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| POST | `/send-otp` | Send OTP to phone/email | `{ "phoneNumber": "...", "email": "..." }` |
| POST | `/verify-otp` | Verify OTP and login | `{ "phoneNumber": "...", "otp": "..." }` |
| POST | `/refresh-token` | Refresh JWT token | `{ "refreshToken": "..." }` |
| POST | `/logout` | Logout user | - |

### 2. User Service
**Port:** 8082
**Base Path:** `/v1/users`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| POST | `/` | Create User | `{ "firstName": "...", "lastName": "..." }` |
| GET | `/me` | Get current user profile | - |
| PUT | `/me` | Update profile | `{ "email": "..." }` |
| POST | `/upgrade-to-driver` | Request driver upgrade | `{ "licenseNumber": "..." }` |
| POST | `/v1/drivers/kyc-documents` | Upload KYC docs | Multipart File |

### 3. Booking Service
**Port:** 8083
**Base Path:** `/v1/bookings`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| POST | `/` | Create new booking | `{ "routeId": "...", "seatCount": 1 }` |
| GET | `/` | List my bookings | - |
| GET | `/upcoming` | Get upcoming bookings | - |
| GET | `/{id}` | Get booking details | - |
| POST | `/{id}/cancel` | Cancel booking | `{ "reason": "..." }` |

### 4. Payment Service
**Port:** 8084
**Base Path:** `/v1/payments`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| POST | `/initiate` | Initiate payment | `{ "bookingId": "...", "amount": 100.00, "method": "CARD" }` |
| POST | `/verify` | Verify payment | `{ "reference": "..." }` |
| GET | `/{id}` | Get payment details | - |
| GET | `/my-payments` | List user payments | - |
| POST | `/webhook` | Payment gateway webhook | (Provider specific) |

### 5. Ticketing Service
**Port:** 8086
**Base Path:** `/v1/tickets`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| POST | `/v1/bookings/{bookingId}/ticket` | Generate ticket | - |
| POST | `/verify` | Verify ticket QR code | `{ "ticketCode": "..." }` |
| POST | `/revoke` | Revoke ticket | `{ "reason": "..." }` |
| GET | `/public-key` | Get validation key | - |

### 6. Payouts Service
**Port:** 8087
**Base Path:** `/v1/payouts`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| POST | `/request` | Request payout | `{ "amount": 500.00, "accountId": "..." }` |
| GET | `/requests` | List payout requests | - |
| GET | `/balance` | Get driver balance | - |

---

## Python Services (snake_case)

### 7. Driver Service
**Port:** 8091
**Base Path:** `/v1` (Inferred)

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| POST | `/routes` | Create route | `{ "start_lat": 1.0, "start_lon": 1.0, "departure_time": "..." }` |
| GET | `/routes` | List driver routes | - |
| GET | `/routes/active` | Get active routes | - |
| GET | `/routes/{id}` | Get route details | - |
| PUT | `/routes/{id}` | Update route | `{ "status": "..." }` |
| PATCH | `/routes/{id}/status` | Update status | `?status=ACTIVE` |
| DELETE | `/routes/{id}` | Delete route | - |

### 8. Matchmaking Service
**Port:** 8092
**Base Path:** `/match`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| POST | `/` | Find matching routes | `{ "origin_lat": 1.0, "origin_lon": 1.0, "desired_time": "..." }` |

### 9. Search Service
**Port:** 8093
**Base Path:** `/routes`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| GET | `/` | Search routes (Public) | `?lat=...&lng=...&radius=5.0` |
| GET | `/{id}` | Get route details | - |

### 10. Fleet Service
**Port:** 8096
**Base Path:** `/v1/trips`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| POST | `/` | Create trip | `{ "booking_id": "...", "driver_id": "..." }` |
| GET | `/{id}` | Get trip details | - |
| PATCH | `/{id}/status` | Update trip status | `{ "status": "STARTED", "estimated_arrival": "..." }` |
| GET | `/driver/{id}` | Get driver trips | - |
| GET | `/rider/{id}` | Get rider trips | - |

### 11. Analytics Service
**Port:** 8097
**Base Path:** `/metrics`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| GET | `/users` | User metrics | `?start_date=...&end_date=...` |
| GET | `/bookings` | Booking metrics | `?start_date=...&end_date=...` |
| GET | `/payments` | Payment metrics | `?start_date=...&end_date=...` |
| GET | `/trips` | Trip metrics | `?start_date=...&end_date=...` |
| GET | `/realtime` | Realtime dashboard | - |
| GET | `/drivers` | Driver performance | - |
| GET | `/routes` | Route performance | - |

### 12. Notification Service
**Port:** 8095
**Base Path:** `/v1/notifications`

| Method | Endpoint | Description | Request Body |
|--------|----------|-------------|--------------|
| POST | `/send` | Send sync notification | `{ "user_id": "...", "type": "BOOKING_CONFIRMED", "channels": ["EMAIL"] }` |
| POST | `/send-async` | Send async notification | `{ "user_id": "...", "type": "...", "data": {} }` |
| POST | `/broadcast` | Broadcast message | `{ "user_ids": [...], "message": "..." }` |
| GET | `/history` | Get user notifications | `?user_id=...` |
| GET | `/{id}` | Get notification details | - |

# Mobile Integration Notes

This document summarizes the API verification findings to assist with mobile frontend integration.

## 1. Authentication Flow (Auth Service)
**Base URL:** `http://<host>:8081/v1/auth`

### Login Step 1: Send OTP
- **Endpoint:** `POST /send-otp`
- **Payload:**
  ```json
  {
    "phone": "+2348012345678"
  }
  ```
- **Response:** `200 OK`

### Login Step 2: Verify OTP
- **Endpoint:** `POST /verify-otp`
- **Payload:**
  ```json
  {
    "phone": "+2348012345678",
    "code": "123456"
  }
  ```
- **Response:**
  ```json
  {
    "success": true,
    "message": "...",
    "data": {
      "accessToken": "eyJhbG...",
      "refreshToken": "d7e8...",
      "user": {
        "id": "uuid-string",
        "phone": "+234...",
        "role": "RIDER",
        "fullName": "John Doe",
        "email": "john@example.com"
      }
    }
  }
  ```
- **Note:** Store `accessToken` and `refreshToken` securely.

### Token Refresh
- **Endpoint:** `POST /refresh-token`
- **Payload:**
  ```json
  {
    "refreshToken": "d7e8..."
  }
  ```

## 2. User Profile (User Service)
**Base URL:** `http://<host>:8082/v1`

- **Get Profile:** `GET /users/me` (Requires `Authorization: Bearer <token>`)
- **Update Profile:** `PATCH /users/me` (Note: Code uses **PATCH**, Docs said PUT)
  - **Payload:**
    ```json
    {
      "fullName": "New Name",
      "email": "new@email.com"
    }
    ```

## 3. Booking Flow (Booking Service)
**Base URL:** `http://<host>:8083/v1/bookings`

- **Create Booking:** `POST /`
  - **Payload:**
    ```json
    {
      "routeId": "uuid",
      "originStopId": "uuid",
      "destinationStopId": "uuid",
      "travelDate": "2025-12-25",
      "seatsBooked": 1,
      "idempotencyKey": "unique-string"
    }
    ```
- **Get Booking:** `GET /{id}`

## 4. Payments (Payment Service)
**Base URL:** `http://<host>:8084/v1/payments`

- **Initiate:** `POST /initiate`
  - **Payload:**
    ```json
    {
      "bookingId": "uuid",
      "amount": 5000.00,
      "currency": "NGN",
      "customerEmail": "email@test.com",
      "customerName": "Name",
      "idempotencyKey": "unique-key"
    }
    ```

## 5. Driver Routes (Driver Service - Python)
**Base URL:** `http://<host>:8091/v1`

- **Create Route:** `POST /drivers/routes`
  - **Note:** The code mounts routes at `/drivers/routes`, differing from some docs which implied `/routes`.
  - **Payload:** (snake_case)
    ```json
    {
      "name": "Lagos to Abuja",
      "departure_time": "08:00:00",
      "seats_total": 14,
      "base_price": 15000,
      "active_days": [0, 1, 2, 3, 4, 5, 6],
      "stops": [...]
    }
    ```

## 6. Matchmaking (Matchmaking Service - Python)
**Base URL:** `http://<host>:8092/v1`

- **Find Matches:** `POST /match`
  - **Payload:** (snake_case)
    ```json
    {
      "origin_lat": 6.5,
      "origin_lon": 3.3,
      "destination_lat": 9.0,
      "destination_lon": 7.4,
      "departure_date": "2025-12-25"
    }
    ```

## General Notes
- **Field Naming:**
  - Java Services (Auth, User, Booking, Payment, Payouts): **camelCase** (e.g., `paymentId`)
  - Python Services (Driver, Matchmaking): **snake_case** (e.g., `route_id`)
- **UUIDs:** Java services may return UUIDs as Strings in JSON. Treat them as strings in the mobile app.
- **Dates:** Use ISO 8601 format (YYYY-MM-DD) and UTC for timestamps.

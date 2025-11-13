# Fleet Service API Documentation

## Overview

The Fleet Service provides real-time driver location tracking and trip monitoring through WebSocket connections and REST APIs.

## Table of Contents

1. [REST API Endpoints](#rest-api-endpoints)
2. [WebSocket Events](#websocket-events)
3. [Authentication](#authentication)
4. [Rate Limiting](#rate-limiting)
5. [Error Handling](#error-handling)

## REST API Endpoints

### Base URL

```
http://localhost:8096/v1
```

### Driver Location Endpoints

#### Update Driver Location

```http
POST /v1/drivers/{driver_id}/location
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "latitude": 37.7749,
  "longitude": -122.4194,
  "bearing": 45.5,
  "speed": 30.0,
  "accuracy": 5.0,
  "altitude": 10.0,
  "timestamp": "2024-12-15T10:30:00Z"
}
```

**Response (200 OK):**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "driver_id": "123e4567-e89b-12d3-a456-426614174001",
  "latitude": 37.7749,
  "longitude": -122.4194,
  "bearing": 45.5,
  "speed": 30.0,
  "accuracy": 5.0,
  "altitude": 10.0,
  "status": "ONLINE",
  "created_at": "2024-12-15T10:30:00Z",
  "updated_at": "2024-12-15T10:30:00Z"
}
```

**Error (429 Too Many Requests):**
```json
{
  "detail": "Location update rate limit: 5s between updates"
}
```

#### Get Driver Location

```http
GET /v1/drivers/{driver_id}/location
Authorization: Bearer <JWT_TOKEN>
```

**Response (200 OK):** Same as Update Driver Location

#### Update Driver Status

```http
POST /v1/drivers/{driver_id}/status
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "status": "ONLINE",
  "location": {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "accuracy": 5.0
  }
}
```

**Status Values:**
- `OFFLINE` - Driver is offline
- `ONLINE` - Driver is available
- `BUSY` - Driver is temporarily unavailable
- `ON_TRIP` - Driver is currently on a trip

**Response:** 204 No Content

#### Find Nearby Drivers

```http
POST /v1/drivers/nearby
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "latitude": 37.7749,
  "longitude": -122.4194,
  "radius_meters": 5000,
  "limit": 10
}
```

**Response (200 OK):**
```json
[
  {
    "driver_id": "123e4567-e89b-12d3-a456-426614174001",
    "latitude": 37.7749,
    "longitude": -122.4194,
    "bearing": 45.5,
    "distance_meters": 1500,
    "status": "ONLINE",
    "updated_at": "2024-12-15T10:30:00Z"
  }
]
```

#### Get Location History

```http
GET /v1/drivers/{driver_id}/history?trip_id={trip_id}&limit=100
Authorization: Bearer <JWT_TOKEN>
```

**Query Parameters:**
- `trip_id` (optional): Filter by trip
- `limit` (optional): Max results (default: 100, max: 1000)

**Response (200 OK):**
```json
[
  {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "driver_id": "123e4567-e89b-12d3-a456-426614174001",
    "trip_id": "123e4567-e89b-12d3-a456-426614174002",
    "latitude": 37.7749,
    "longitude": -122.4194,
    "bearing": 45.5,
    "speed": 30.0,
    "accuracy": 5.0,
    "recorded_at": "2024-12-15T10:30:00Z"
  }
]
```

### Trip Tracking Endpoints

#### Create Trip

```http
POST /v1/trips
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "trip_id": "123e4567-e89b-12d3-a456-426614174000",
  "booking_id": "123e4567-e89b-12d3-a456-426614174001",
  "driver_id": "123e4567-e89b-12d3-a456-426614174002",
  "rider_id": "123e4567-e89b-12d3-a456-426614174003",
  "pickup_location": {
    "latitude": 37.7749,
    "longitude": -122.4194
  },
  "dropoff_location": {
    "latitude": 37.8049,
    "longitude": -122.4394
  },
  "scheduled_time": "2024-12-15T11:00:00Z"
}
```

**Response (201 Created):**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "trip_id": "123e4567-e89b-12d3-a456-426614174001",
  "booking_id": "123e4567-e89b-12d3-a456-426614174002",
  "driver_id": "123e4567-e89b-12d3-a456-426614174003",
  "rider_id": "123e4567-e89b-12d3-a456-426614174004",
  "status": "PENDING",
  "pickup_location": {
    "latitude": 37.7749,
    "longitude": -122.4194
  },
  "dropoff_location": {
    "latitude": 37.8049,
    "longitude": -122.4394
  },
  "scheduled_time": "2024-12-15T11:00:00Z",
  "created_at": "2024-12-15T10:30:00Z",
  "updated_at": "2024-12-15T10:30:00Z"
}
```

#### Get Trip

```http
GET /v1/trips/{trip_id}
Authorization: Bearer <JWT_TOKEN>
```

**Response (200 OK):** Same as Create Trip

#### Update Trip Status

```http
PATCH /v1/trips/{trip_id}/status
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

{
  "status": "EN_ROUTE",
  "estimated_arrival": "2024-12-15T11:15:00Z"
}
```

**Trip Status Lifecycle:**
```
PENDING → EN_ROUTE → ARRIVED → IN_PROGRESS → COMPLETED
          ↓           ↓         ↓
          CANCELLED   CANCELLED CANCELLED
```

**Response (200 OK):** Same as Get Trip

#### Get Driver Trips

```http
GET /v1/trips/driver/{driver_id}?status=EN_ROUTE&limit=50&offset=0
Authorization: Bearer <JWT_TOKEN>
```

**Query Parameters:**
- `status` (optional): Filter by status
- `limit` (optional): Max results (default: 50, max: 100)
- `offset` (optional): Pagination offset (default: 0)

**Response (200 OK):** Array of trips

#### Get Rider Trips

```http
GET /v1/trips/rider/{rider_id}?status=EN_ROUTE&limit=50&offset=0
Authorization: Bearer <JWT_TOKEN>
```

**Query Parameters:** Same as Get Driver Trips

**Response (200 OK):** Array of trips

### Health Check Endpoints

#### Health Check

```http
GET /health
```

**Response (200 OK):**
```json
{
  "status": "healthy",
  "service": "fleet-service",
  "timestamp": "2024-12-15T10:30:00Z",
  "checks": {
    "database": "healthy",
    "redis": "healthy"
  }
}
```

#### Readiness Check

```http
GET /ready
```

**Response (200 OK):**
```json
{
  "status": "ready"
}
```

#### Liveness Check

```http
GET /live
```

**Response (200 OK):**
```json
{
  "status": "alive"
}
```

## WebSocket Events

### Connection

Connect to WebSocket server at: `ws://localhost:8096/socket.io`

**Authentication:**

Send JWT token in connection handshake:

```javascript
const socket = io('http://localhost:8096', {
  path: '/socket.io',
  auth: {
    token: 'your-jwt-token'
  }
});
```

### Driver Events

#### driver:location (emit from driver)

Send location update from driver app:

```javascript
socket.emit('driver:location', {
  latitude: 37.7749,
  longitude: -122.4194,
  bearing: 45.5,
  speed: 30.0,
  accuracy: 5.0,
  altitude: 10.0
});
```

#### driver:online (emit from driver)

Set driver status to online:

```javascript
socket.emit('driver:online', {
  location: {
    latitude: 37.7749,
    longitude: -122.4194,
    accuracy: 5.0
  }
});
```

**Response:**
```javascript
socket.on('status_updated', (data) => {
  console.log(data);
  // { status: 'ONLINE', timestamp: '2024-12-15T10:30:00Z' }
});
```

#### driver:offline (emit from driver)

Set driver status to offline:

```javascript
socket.emit('driver:offline', {});
```

### Rider Events

#### rider:subscribe (emit from rider)

Subscribe to trip updates:

```javascript
socket.emit('rider:subscribe', {
  trip_id: '123e4567-e89b-12d3-a456-426614174001',
  rider_id: '123e4567-e89b-12d3-a456-426614174002'
});
```

**Response:**
```javascript
socket.on('trip_status', (data) => {
  console.log(data);
  // {
  //   trip_id: '...',
  //   status: 'EN_ROUTE',
  //   driver_id: '...',
  //   estimated_arrival: '2024-12-15T11:15:00Z'
  // }
});
```

#### rider:unsubscribe (emit from rider)

Unsubscribe from trip updates:

```javascript
socket.emit('rider:unsubscribe', {
  trip_id: '123e4567-e89b-12d3-a456-426614174001',
  rider_id: '123e4567-e89b-12d3-a456-426614174002'
});
```

### Server Events (received by clients)

#### connected

Sent when connection is established:

```javascript
socket.on('connected', (data) => {
  console.log(data);
  // {
  //   user_id: '...',
  //   role: 'DRIVER',
  //   timestamp: '2024-12-15T10:30:00Z'
  // }
});
```

#### driver:location (broadcast)

Broadcasted to trip riders when driver location updates:

```javascript
socket.on('driver:location', (data) => {
  console.log(data);
  // {
  //   driver_id: '...',
  //   latitude: 37.7749,
  //   longitude: -122.4194,
  //   bearing: 45.5,
  //   speed: 30.0,
  //   status: 'ON_TRIP',
  //   timestamp: '2024-12-15T10:30:00Z'
  // }
});
```

#### trip:update (broadcast)

Broadcasted to trip riders when trip status changes:

```javascript
socket.on('trip:update', (data) => {
  console.log(data);
  // {
  //   trip_id: '...',
  //   status: 'ARRIVED',
  //   estimated_arrival: '2024-12-15T11:15:00Z',
  //   timestamp: '2024-12-15T10:30:00Z'
  // }
});
```

#### error

Sent when an error occurs:

```javascript
socket.on('error', (data) => {
  console.error(data);
  // {
  //   error_code: 'RATE_LIMIT_EXCEEDED',
  //   message: 'Location update rate limit exceeded',
  //   details: { retry_after: 5 }
  // }
});
```

## Authentication

All REST API endpoints require JWT authentication using Bearer token:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

WebSocket connections require JWT token in auth handshake (see WebSocket Events section).

### JWT Payload Structure

```json
{
  "sub": "user-uuid",
  "role": "DRIVER",
  "exp": 1702645800,
  "iat": 1702559400
}
```

**Roles:**
- `DRIVER` - Can update location, change status
- `RIDER` - Can subscribe to trip updates, view trip status

## Rate Limiting

### Location Updates

- **Limit:** 1 update per 5 seconds per driver
- **Status Code:** 429 Too Many Requests
- **Response:** `{"detail": "Location update rate limit: 5s between updates"}`

### WebSocket Connections

- **Limit:** Maximum 3 concurrent connections per driver
- **Behavior:** New connection rejected with error event

### REST API

- **Limit:** 60 requests per minute per IP address
- **Status Code:** 429 Too Many Requests

## Error Handling

### HTTP Status Codes

- `200 OK` - Request successful
- `201 Created` - Resource created successfully
- `204 No Content` - Request successful, no response body
- `400 Bad Request` - Invalid request data
- `401 Unauthorized` - Missing or invalid authentication
- `403 Forbidden` - Insufficient permissions
- `404 Not Found` - Resource not found
- `429 Too Many Requests` - Rate limit exceeded
- `500 Internal Server Error` - Server error

### Error Response Format

```json
{
  "detail": "Error message describing what went wrong"
}
```

### WebSocket Errors

```javascript
socket.on('error', (data) => {
  // {
  //   error_code: 'ERROR_CODE',
  //   message: 'Human-readable message',
  //   details: { ... }
  // }
});
```

**Common Error Codes:**
- `AUTHENTICATION_FAILED` - JWT token invalid or expired
- `UNAUTHORIZED` - Insufficient permissions for action
- `RATE_LIMIT_EXCEEDED` - Too many requests
- `MAX_CONNECTIONS_EXCEEDED` - Too many concurrent connections
- `TRIP_NOT_FOUND` - Trip not found or access denied
- `LOCATION_UPDATE_FAILED` - Failed to update location
- `STATUS_UPDATE_FAILED` - Failed to update status
- `SUBSCRIBE_FAILED` - Failed to subscribe to trip updates

## OpenAPI Documentation

Interactive API documentation available at:

- Swagger UI: `http://localhost:8096/docs`
- ReDoc: `http://localhost:8096/redoc`
- OpenAPI JSON: `http://localhost:8096/openapi.json`

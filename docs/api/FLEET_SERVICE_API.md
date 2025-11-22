# Fleet Service API Documentation

## Service Information
- **Service Name:** Fleet Service
- **Port:** 8096
- **Base URL:** `/v1`
- **Technology:** Python / FastAPI
- **Description:** Real-time trip tracking, fleet management, and live location updates with WebSocket support for active trips

---

## Authentication

All endpoints require JWT authentication. WebSocket connections also require authentication via token.

**Request Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

---

## Field Naming Convention

**⚠️ IMPORTANT:** This is a Python service. All JSON fields use **snake_case** (not camelCase).

---

## Endpoints

### 1. Create Trip Instance

Create a trip instance from a scheduled route.

**Endpoint:** `POST /trips`

**Authentication:** Required (DRIVER role)

**Request Body:**
```json
{
  "route_id": "456e4567-e89b-12d3-a456-426614174001",
  "scheduled_date": "2025-11-25",
  "vehicle_id": "123e4567-e89b-12d3-a456-426614174000",
  "notes": "On time departure expected"
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| route_id | UUID | Yes | Valid route | Route to instantiate |
| scheduled_date | date | Yes | YYYY-MM-DD | Trip date |
| vehicle_id | UUID | Yes | Driver's vehicle | Vehicle for trip |
| notes | string | No | Max 1000 chars | Trip notes |

**Success Response (201 Created):**
```json
{
  "id": "567e4567-e89b-12d3-a456-426614174007",
  "route_id": "456e4567-e89b-12d3-a456-426614174001",
  "driver_id": "234e4567-e89b-12d3-a456-426614174001",
  "vehicle_id": "123e4567-e89b-12d3-a456-426614174000",
  "scheduled_date": "2025-11-25",
  "scheduled_departure": "2025-11-25T08:00:00Z",
  "status": "SCHEDULED",
  "seats_total": 14,
  "seats_booked": 4,
  "seats_available": 10,
  "current_location": null,
  "current_stop_index": null,
  "created_at": "2025-11-20T10:00:00Z",
  "updated_at": "2025-11-20T10:00:00Z"
}
```

**Error Responses:**

**400 Bad Request:**
```json
{
  "detail": "Trip already exists for this route and date"
}
```

**403 Forbidden:**
```json
{
  "detail": "Not authorized to create trip for this route"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8096/trips \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "route_id": "456e4567-e89b-12d3-a456-426614174001",
    "scheduled_date": "2025-11-25",
    "vehicle_id": "123e4567-e89b-12d3-a456-426614174000"
  }'
```

---

### 2. Start Trip

Start an active trip and begin tracking.

**Endpoint:** `POST /trips/{trip_id}/start`

**Authentication:** Required (DRIVER role)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| trip_id | UUID | Yes | Trip ID to start |

**Request Body:**
```json
{
  "starting_lat": 6.5833,
  "starting_lon": 3.3833,
  "odometer_start": 125000
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| starting_lat | decimal | Yes | Current latitude |
| starting_lon | decimal | Yes | Current longitude |
| odometer_start | integer | No | Vehicle odometer reading |

**Success Response (200 OK):**
```json
{
  "id": "567e4567-e89b-12d3-a456-426614174007",
  "status": "IN_PROGRESS",
  "actual_departure": "2025-11-25T08:05:00Z",
  "current_location": {
    "lat": 6.5833,
    "lon": 3.3833,
    "timestamp": "2025-11-25T08:05:00Z"
  },
  "current_stop_index": 0,
  "tracking_session_id": "789e4567-e89b-12d3-a456-426614174008"
}
```

**Error Responses:**

**400 Bad Request:**
```json
{
  "detail": "Trip already started"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8096/trips/567e4567-e89b-12d3-a456-426614174007/start \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "starting_lat": 6.5833,
    "starting_lon": 3.3833
  }'
```

---

### 3. Update Trip Location

Update current location during active trip.

**Endpoint:** `POST /trips/{trip_id}/location`

**Authentication:** Required (DRIVER role)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| trip_id | UUID | Yes | Active trip ID |

**Request Body:**
```json
{
  "lat": 6.6500,
  "lon": 3.4200,
  "speed_kmh": 65.5,
  "heading": 45,
  "accuracy_meters": 10
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| lat | decimal | Yes | Current latitude |
| lon | decimal | Yes | Current longitude |
| speed_kmh | decimal | No | Current speed |
| heading | integer | No | Direction (0-359 degrees) |
| accuracy_meters | decimal | No | GPS accuracy |

**Success Response (200 OK):**
```json
{
  "trip_id": "567e4567-e89b-12d3-a456-426614174007",
  "location_updated": true,
  "timestamp": "2025-11-25T08:15:00Z",
  "next_stop": {
    "name": "Challenge Bus Stop",
    "distance_km": 110.5,
    "eta_minutes": 75
  }
}
```

**Error Responses:**

**400 Bad Request:**
```json
{
  "detail": "Trip not in progress"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8096/trips/567e4567-e89b-12d3-a456-426614174007/location \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "lat": 6.6500,
    "lon": 3.4200,
    "speed_kmh": 65.5
  }'
```

---

### 4. Complete Trip

Mark trip as completed.

**Endpoint:** `POST /trips/{trip_id}/complete`

**Authentication:** Required (DRIVER role)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| trip_id | UUID | Yes | Trip ID to complete |

**Request Body:**
```json
{
  "ending_lat": 7.3833,
  "ending_lon": 3.9000,
  "odometer_end": 125125,
  "notes": "Trip completed successfully"
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| ending_lat | decimal | Yes | Final latitude |
| ending_lon | decimal | Yes | Final longitude |
| odometer_end | integer | No | Final odometer reading |
| notes | string | No | Completion notes |

**Success Response (200 OK):**
```json
{
  "id": "567e4567-e89b-12d3-a456-426614174007",
  "status": "COMPLETED",
  "actual_departure": "2025-11-25T08:05:00Z",
  "actual_arrival": "2025-11-25T09:35:00Z",
  "duration_minutes": 90,
  "distance_traveled_km": 125,
  "completed_at": "2025-11-25T09:35:00Z"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8096/trips/567e4567-e89b-12d3-a456-426614174007/complete \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "ending_lat": 7.3833,
    "ending_lon": 3.9000
  }'
```

---

### 5. Cancel Trip

Cancel a scheduled or in-progress trip.

**Endpoint:** `POST /trips/{trip_id}/cancel`

**Authentication:** Required (DRIVER role)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| trip_id | UUID | Yes | Trip ID to cancel |

**Request Body:**
```json
{
  "reason": "Vehicle breakdown",
  "notify_passengers": true
}
```

**Request Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| reason | string | Yes | Cancellation reason |
| notify_passengers | boolean | No | Send notifications (default: true) |

**Success Response (200 OK):**
```json
{
  "id": "567e4567-e89b-12d3-a456-426614174007",
  "status": "CANCELLED",
  "cancellation_reason": "Vehicle breakdown",
  "cancelled_at": "2025-11-25T07:50:00Z",
  "passengers_notified": true
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8096/trips/567e4567-e89b-12d3-a456-426614174007/cancel \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "Vehicle breakdown",
    "notify_passengers": true
  }'
```

---

### 6. Get Trip Details

Get detailed trip information.

**Endpoint:** `GET /trips/{trip_id}`

**Authentication:** Required (DRIVER or booked RIDER)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| trip_id | UUID | Yes | Trip ID |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| include_tracking | boolean | No | false | Include location history |
| include_bookings | boolean | No | false | Include booking details |

**Success Response (200 OK):**
```json
{
  "id": "567e4567-e89b-12d3-a456-426614174007",
  "route": {
    "id": "456e4567-e89b-12d3-a456-426614174001",
    "name": "Lagos - Ibadan Express"
  },
  "driver": {
    "id": "234e4567-e89b-12d3-a456-426614174001",
    "name": "John Doe"
  },
  "vehicle": {
    "make": "Toyota",
    "model": "Hiace",
    "plate_number": "LAG-123-XY"
  },
  "scheduled_date": "2025-11-25",
  "scheduled_departure": "2025-11-25T08:00:00Z",
  "actual_departure": "2025-11-25T08:05:00Z",
  "actual_arrival": null,
  "status": "IN_PROGRESS",
  "seats_total": 14,
  "seats_booked": 4,
  "seats_available": 10,
  "current_location": {
    "lat": 6.6500,
    "lon": 3.4200,
    "timestamp": "2025-11-25T08:15:00Z",
    "speed_kmh": 65.5
  },
  "current_stop_index": 0,
  "next_stop": {
    "name": "Challenge Bus Stop",
    "eta_minutes": 75,
    "distance_km": 110.5
  },
  "tracking_history": [
    {
      "lat": 6.5833,
      "lon": 3.3833,
      "timestamp": "2025-11-25T08:05:00Z",
      "speed_kmh": 0
    },
    {
      "lat": 6.6500,
      "lon": 3.4200,
      "timestamp": "2025-11-25T08:15:00Z",
      "speed_kmh": 65.5
    }
  ],
  "created_at": "2025-11-20T10:00:00Z",
  "updated_at": "2025-11-25T08:15:00Z"
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8096/trips/567e4567-e89b-12d3-a456-426614174007?include_tracking=true" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 7. List Driver Trips

Get trips for current driver.

**Endpoint:** `GET /trips`

**Authentication:** Required (DRIVER role)

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| status | string | No | null | Filter: SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED |
| start_date | date | No | null | Filter from date |
| end_date | date | No | null | Filter to date |
| page | integer | No | 1 | Page number |
| page_size | integer | No | 20 | Results per page (1-100) |

**Success Response (200 OK):**
```json
{
  "trips": [
    {
      "id": "567e4567-e89b-12d3-a456-426614174007",
      "route_name": "Lagos - Ibadan Express",
      "scheduled_date": "2025-11-25",
      "scheduled_departure": "2025-11-25T08:00:00Z",
      "status": "IN_PROGRESS",
      "seats_booked": 4,
      "seats_total": 14
    }
  ],
  "pagination": {
    "page": 1,
    "page_size": 20,
    "total_results": 1,
    "total_pages": 1
  }
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8096/trips?status=IN_PROGRESS" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 8. Track Trip (Rider)

Get real-time trip tracking for booked trip.

**Endpoint:** `GET /trips/{trip_id}/track`

**Authentication:** Required (RIDER with booking for this trip)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| trip_id | UUID | Yes | Trip ID |

**Success Response (200 OK):**
```json
{
  "trip_id": "567e4567-e89b-12d3-a456-426614174007",
  "status": "IN_PROGRESS",
  "driver": {
    "name": "John Doe",
    "phone": "+234***1234",
    "rating": 4.8
  },
  "vehicle": {
    "make": "Toyota",
    "model": "Hiace",
    "plate_number": "LAG-123-XY"
  },
  "current_location": {
    "lat": 6.6500,
    "lon": 3.4200,
    "timestamp": "2025-11-25T08:15:00Z"
  },
  "your_pickup": {
    "stop_name": "Ojota Bus Stop",
    "lat": 6.5833,
    "lon": 3.3833,
    "eta_minutes": 0,
    "status": "completed"
  },
  "your_dropoff": {
    "stop_name": "Challenge Bus Stop",
    "lat": 7.3833,
    "lon": 3.9000,
    "eta_minutes": 75,
    "status": "pending"
  },
  "estimated_arrival": "2025-11-25T09:30:00Z"
}
```

**Example cURL:**
```bash
curl -X GET http://localhost:8096/trips/567e4567-e89b-12d3-a456-426614174007/track \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

## WebSocket: Live Trip Tracking

### Connect to Trip Updates

Real-time location updates via WebSocket.

**WebSocket Endpoint:** `ws://localhost:8096/ws/trips/{trip_id}`

**Authentication:** Query parameter `?token=<access_token>`

**Connection:**
```javascript
const ws = new WebSocket(
  `ws://localhost:8096/ws/trips/${tripId}?token=${accessToken}`
);

ws.onopen = () => {
  console.log('Connected to trip tracking');
};

ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  console.log('Location update:', data);
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};

ws.onclose = () => {
  console.log('Disconnected from trip tracking');
};
```

**Message Types:**

#### Location Update
```json
{
  "type": "location_update",
  "trip_id": "567e4567-e89b-12d3-a456-426614174007",
  "location": {
    "lat": 6.6500,
    "lon": 3.4200,
    "speed_kmh": 65.5,
    "heading": 45,
    "timestamp": "2025-11-25T08:15:00Z"
  },
  "next_stop": {
    "name": "Challenge Bus Stop",
    "eta_minutes": 75,
    "distance_km": 110.5
  }
}
```

#### Status Update
```json
{
  "type": "status_update",
  "trip_id": "567e4567-e89b-12d3-a456-426614174007",
  "old_status": "SCHEDULED",
  "new_status": "IN_PROGRESS",
  "timestamp": "2025-11-25T08:05:00Z"
}
```

#### Stop Arrival
```json
{
  "type": "stop_arrival",
  "trip_id": "567e4567-e89b-12d3-a456-426614174007",
  "stop": {
    "name": "Challenge Bus Stop",
    "lat": 7.3833,
    "lon": 3.9000
  },
  "stop_index": 1,
  "timestamp": "2025-11-25T09:35:00Z"
}
```

---

## Trip Status Flow

```
SCHEDULED → IN_PROGRESS → COMPLETED
    ↓
CANCELLED
```

**Status Descriptions:**

| Status | Description | Transitions |
|--------|-------------|-------------|
| SCHEDULED | Trip created, not started | → IN_PROGRESS, CANCELLED |
| IN_PROGRESS | Trip active, tracking enabled | → COMPLETED, CANCELLED |
| COMPLETED | Trip finished successfully | None |
| CANCELLED | Trip cancelled by driver | None |

---

## Data Models

### Trip Response Schema

```typescript
interface TripResponse {
  id: string;                          // UUID
  route_id: string;                    // UUID
  driver_id: string;                   // UUID
  vehicle_id: string;                  // UUID
  scheduled_date: string;              // YYYY-MM-DD
  scheduled_departure: string;         // ISO 8601
  actual_departure: string | null;     // ISO 8601
  actual_arrival: string | null;       // ISO 8601
  status: TripStatus;
  seats_total: number;
  seats_booked: number;
  seats_available: number;
  current_location: LocationPoint | null;
  current_stop_index: number | null;
  tracking_session_id: string | null;  // UUID
  created_at: string;                  // ISO 8601
  updated_at: string;                  // ISO 8601
}

interface LocationPoint {
  lat: number;
  lon: number;
  timestamp: string;                   // ISO 8601
  speed_kmh?: number;
  heading?: number;                    // 0-359 degrees
  accuracy_meters?: number;
}

type TripStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
```

---

## Rate Limiting

| Action | Limit | Window |
|--------|-------|--------|
| Create Trip | 10 requests | Per hour |
| Update Location | 120 requests | Per minute |
| Get Trip Details | 100 requests | Per minute |
| WebSocket Connections | 5 connections | Per user |

---

## Error Handling

### Common Error Responses

| Code | Error | Description |
|------|-------|-------------|
| 400 | Bad Request | Invalid data, trip already started/completed |
| 401 | Unauthorized | Invalid or missing token |
| 403 | Forbidden | Not authorized for this trip |
| 404 | Not Found | Trip not found |
| 422 | Unprocessable Entity | Validation failed |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |

---

## Integration Notes for Frontend

### Complete Trip Management Flow

```javascript
// 1. Create trip instance
async function createTrip(routeId, date, vehicleId) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch('http://localhost:8096/trips', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      route_id: routeId,
      scheduled_date: date,
      vehicle_id: vehicleId
    })
  });
  return response.json();
}

// 2. Start trip
async function startTrip(tripId, lat, lon) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(
    `http://localhost:8096/trips/${tripId}/start`,
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        starting_lat: lat,
        starting_lon: lon
      })
    }
  );
  return response.json();
}

// 3. Update location (called periodically)
async function updateTripLocation(tripId, lat, lon, speed = null) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(
    `http://localhost:8096/trips/${tripId}/location`,
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        lat,
        lon,
        speed_kmh: speed
      })
    }
  );
  return response.json();
}

// 4. Complete trip
async function completeTrip(tripId, lat, lon) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(
    `http://localhost:8096/trips/${tripId}/complete`,
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        ending_lat: lat,
        ending_lon: lon
      })
    }
  );
  return response.json();
}
```

### Real-Time Tracking Component

```javascript
class TripTracker {
  constructor(tripId, accessToken) {
    this.tripId = tripId;
    this.token = accessToken;
    this.ws = null;
    this.locationUpdateInterval = null;
  }
  
  // For drivers: send location updates
  startTracking() {
    // Get location every 10 seconds
    this.locationUpdateInterval = setInterval(async () => {
      if ('geolocation' in navigator) {
        navigator.geolocation.getCurrentPosition(
          async (position) => {
            await updateTripLocation(
              this.tripId,
              position.coords.latitude,
              position.coords.longitude,
              position.coords.speed * 3.6 // m/s to km/h
            );
          },
          (error) => {
            console.error('Location error:', error);
          },
          { enableHighAccuracy: true }
        );
      }
    }, 10000);
  }
  
  stopTracking() {
    if (this.locationUpdateInterval) {
      clearInterval(this.locationUpdateInterval);
    }
  }
  
  // For riders: receive location updates
  connectWebSocket() {
    this.ws = new WebSocket(
      `ws://localhost:8096/ws/trips/${this.tripId}?token=${this.token}`
    );
    
    this.ws.onmessage = (event) => {
      const data = JSON.parse(event.data);
      this.handleUpdate(data);
    };
    
    this.ws.onerror = (error) => {
      console.error('WebSocket error:', error);
      // Attempt reconnection
      setTimeout(() => this.connectWebSocket(), 5000);
    };
  }
  
  handleUpdate(data) {
    switch (data.type) {
      case 'location_update':
        this.onLocationUpdate(data.location, data.next_stop);
        break;
      case 'status_update':
        this.onStatusChange(data.new_status);
        break;
      case 'stop_arrival':
        this.onStopArrival(data.stop);
        break;
    }
  }
  
  onLocationUpdate(location, nextStop) {
    // Update map marker
    console.log('Vehicle at:', location);
    console.log('Next stop:', nextStop);
  }
  
  onStatusChange(newStatus) {
    console.log('Trip status:', newStatus);
  }
  
  onStopArrival(stop) {
    console.log('Arrived at:', stop.name);
  }
  
  disconnect() {
    if (this.ws) {
      this.ws.close();
    }
    this.stopTracking();
  }
}
```

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-21 | Initial API documentation |

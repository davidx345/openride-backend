# Driver Service API Documentation

## Service Information
- **Service Name:** Driver Service
- **Port:** 8091
- **Base URL:** `/v1` (inferred, routes under `/routes`)
- **Technology:** Python / FastAPI
- **Description:** Manages driver routes, stops, schedules, and vehicle assignments

---

## Authentication

All endpoints require JWT authentication.

**Request Headers:**
```
Authorization: Bearer <access_token>
Content-Type: application/json
```

---

## Field Naming Convention

**⚠️ IMPORTANT:** This is a Python service. All JSON fields use **snake_case** (not camelCase).

Examples:
- `route_id` not `routeId`
- `departure_time` not `departureTime`
- `seats_available` not `seatsAvailable`

---

## Endpoints

### 1. Create Route

Create a new route with stops.

**Endpoint:** `POST /routes`

**Authentication:** Required (DRIVER role, KYC verified)

**Request Body:**
```json
{
  "vehicle_id": "123e4567-e89b-12d3-a456-426614174000",
  "name": "Lagos - Ibadan Express",
  "departure_time": "08:00:00",
  "active_days": [0, 1, 2, 3, 4],
  "seats_total": 14,
  "base_price": 3500.00,
  "schedule_rrule": "FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR",
  "notes": "Comfortable ride with AC",
  "stops": [
    {
      "name": "Ojota Bus Stop",
      "lat": 6.5833,
      "lon": 3.3833,
      "address": "Ojota, Lagos",
      "landmark": "Near Ojota Police Station",
      "planned_arrival_offset_minutes": 0,
      "price_from_origin": 0.00
    },
    {
      "name": "Challenge Bus Stop",
      "lat": 7.3833,
      "lon": 3.9000,
      "address": "Challenge, Ibadan",
      "landmark": "Challenge Roundabout",
      "planned_arrival_offset_minutes": 90,
      "price_from_origin": 3500.00
    }
  ]
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| vehicle_id | UUID | Yes | Valid vehicle | Driver's vehicle |
| name | string | Yes | 1-255 chars | Route name |
| departure_time | time | Yes | HH:MM:SS | Daily departure time |
| active_days | array | Yes | 1-7 items, 0-6 | Days of week (0=Mon, 6=Sun) |
| seats_total | integer | Yes | 1-50 | Total seats in vehicle |
| base_price | decimal | Yes | ≥ 0 | Base fare in NGN |
| schedule_rrule | string | No | Max 500 chars | iCal RRULE format |
| notes | string | No | Max 1000 chars | Additional information |
| stops | array | Yes | Min 2 stops | Route stops (origin + destination + waypoints) |

**Stop Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| stop_id | UUID | No | Existing stop | Use existing stop |
| name | string | Conditional | 1-255 chars | Stop name (required if no stop_id) |
| lat | decimal | Conditional | -90 to 90 | Latitude (required if no stop_id) |
| lon | decimal | Conditional | -180 to 180 | Longitude (required if no stop_id) |
| address | string | No | Max 500 chars | Full address |
| landmark | string | No | Max 255 chars | Nearby landmark |
| planned_arrival_offset_minutes | integer | Yes | 0-1440 | Minutes from departure |
| price_from_origin | decimal | Yes | ≥ 0 | Cumulative price from origin |

**Success Response (201 Created):**
```json
{
  "id": "456e4567-e89b-12d3-a456-426614174001",
  "driver_id": "234e4567-e89b-12d3-a456-426614174001",
  "vehicle_id": "123e4567-e89b-12d3-a456-426614174000",
  "name": "Lagos - Ibadan Express",
  "departure_time": "08:00:00",
  "active_days": [0, 1, 2, 3, 4],
  "seats_total": 14,
  "seats_available": 14,
  "base_price": 3500.00,
  "schedule_rrule": "FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR",
  "notes": "Comfortable ride with AC",
  "status": "ACTIVE",
  "stops": [
    {
      "id": "567e4567-e89b-12d3-a456-426614174002",
      "stop_id": "678e4567-e89b-12d3-a456-426614174003",
      "stop_order": 0,
      "stop_name": "Ojota Bus Stop",
      "stop_lat": 6.5833,
      "stop_lon": 3.3833,
      "planned_arrival_offset_minutes": 0,
      "price_from_origin": 0.00
    },
    {
      "id": "567e4567-e89b-12d3-a456-426614174004",
      "stop_id": "678e4567-e89b-12d3-a456-426614174005",
      "stop_order": 1,
      "stop_name": "Challenge Bus Stop",
      "stop_lat": 7.3833,
      "stop_lon": 3.9000,
      "planned_arrival_offset_minutes": 90,
      "price_from_origin": 3500.00
    }
  ],
  "created_at": "2025-11-21T10:30:00.000Z",
  "updated_at": "2025-11-21T10:30:00.000Z"
}
```

**Error Responses:**

**400 Bad Request:**
```json
{
  "detail": "First stop must have arrival offset of 0"
}
```

**403 Forbidden:**
```json
{
  "detail": "Driver KYC not verified"
}
```

**429 Too Many Requests:**
```json
{
  "detail": "Rate limit exceeded: maximum 5 routes per hour"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8091/routes \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "vehicle_id": "123e4567-e89b-12d3-a456-426614174000",
    "name": "Lagos - Ibadan Express",
    "departure_time": "08:00:00",
    "active_days": [0, 1, 2, 3, 4],
    "seats_total": 14,
    "base_price": 3500.00,
    "stops": [...]
  }'
```

---

### 2. List Driver Routes

Get all routes for current driver.

**Endpoint:** `GET /routes`

**Authentication:** Required (DRIVER role)

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| status | string | No | null | Filter: ACTIVE, PAUSED, CANCELLED |
| include_stops | boolean | No | true | Include stop details |

**Success Response (200 OK):**
```json
[
  {
    "id": "456e4567-e89b-12d3-a456-426614174001",
    "driver_id": "234e4567-e89b-12d3-a456-426614174001",
    "name": "Lagos - Ibadan Express",
    "departure_time": "08:00:00",
    "active_days": [0, 1, 2, 3, 4],
    "seats_total": 14,
    "seats_available": 10,
    "base_price": 3500.00,
    "status": "ACTIVE",
    "stops": [...],
    "created_at": "2025-11-21T10:30:00.000Z",
    "updated_at": "2025-11-21T10:30:00.000Z"
  }
]
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8091/routes?status=ACTIVE&include_stops=true" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 3. Get Active Routes

Get only active routes for current driver.

**Endpoint:** `GET /routes/active`

**Authentication:** Required (DRIVER role)

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| include_stops | boolean | No | true | Include stop details |
| limit | integer | No | null | Max results (1-100) |

**Success Response (200 OK):**

Returns array of route objects (same structure as List Routes).

**Example cURL:**
```bash
curl -X GET "http://localhost:8091/routes/active?limit=10" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 4. Get Route by ID

Get specific route details.

**Endpoint:** `GET /routes/{route_id}`

**Authentication:** Required (DRIVER role)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| route_id | UUID | Yes | Route ID |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| include_stops | boolean | No | true | Include stop details |

**Success Response (200 OK):**

Returns single route object (same structure as Create Route response).

**Error Responses:**

**404 Not Found:**
```json
{
  "detail": "Route not found"
}
```

**403 Forbidden:**
```json
{
  "detail": "You do not have access to this route"
}
```

**Example cURL:**
```bash
curl -X GET http://localhost:8091/routes/456e4567-e89b-12d3-a456-426614174001 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 5. Update Route

Update route details.

**Endpoint:** `PUT /routes/{route_id}`

**Authentication:** Required (DRIVER role)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| route_id | UUID | Yes | Route ID to update |

**Request Body:**
```json
{
  "name": "Lagos - Ibadan Express (Updated)",
  "departure_time": "09:00:00",
  "active_days": [0, 1, 2, 3, 4, 5],
  "base_price": 4000.00,
  "notes": "Now includes Saturday service"
}
```

**Request Fields (all optional):**

| Field | Type | Validation | Description |
|-------|------|------------|-------------|
| name | string | 1-255 chars | Route name |
| departure_time | time | HH:MM:SS | Departure time |
| active_days | array | 1-7 items, 0-6 | Active days |
| base_price | decimal | ≥ 0 | Base price |
| notes | string | Max 1000 chars | Notes |
| status | string | ACTIVE, PAUSED, CANCELLED | Route status |

**Success Response (200 OK):**

Returns updated route object.

**Error Responses:**

**400 Bad Request:**
```json
{
  "detail": "Cannot update route with active bookings"
}
```

**Example cURL:**
```bash
curl -X PUT http://localhost:8091/routes/456e4567-e89b-12d3-a456-426614174001 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Lagos - Ibadan Express (Updated)",
    "base_price": 4000.00
  }'
```

---

### 6. Update Route Status

Update only the route status.

**Endpoint:** `PATCH /routes/{route_id}/status`

**Authentication:** Required (DRIVER role)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| route_id | UUID | Yes | Route ID |

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| status | string | Yes | New status: ACTIVE, PAUSED, CANCELLED |

**Success Response (200 OK):**

Returns updated route object.

**Example cURL:**
```bash
curl -X PATCH "http://localhost:8091/routes/456e4567-e89b-12d3-a456-426614174001/status?status=PAUSED" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 7. Delete Route

Delete (cancel) a route.

**Endpoint:** `DELETE /routes/{route_id}`

**Authentication:** Required (DRIVER role)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| route_id | UUID | Yes | Route ID to delete |

**Success Response (204 No Content):**

No response body.

**Error Responses:**

**400 Bad Request:**
```json
{
  "detail": "Cannot delete route with confirmed bookings"
}
```

**Example cURL:**
```bash
curl -X DELETE http://localhost:8091/routes/456e4567-e89b-12d3-a456-426614174001 \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

## Route Status Flow

```
ACTIVE ⟷ PAUSED → CANCELLED
```

**Status Descriptions:**

| Status | Description | Visible to Riders | Can Accept Bookings |
|--------|-------------|-------------------|---------------------|
| ACTIVE | Route is active and accepting bookings | Yes | Yes |
| PAUSED | Temporarily paused by driver | No | No |
| CANCELLED | Permanently cancelled | No | No |

---

## Active Days Format

Days of the week are represented as integers:

| Integer | Day |
|---------|-----|
| 0 | Monday |
| 1 | Tuesday |
| 2 | Wednesday |
| 3 | Thursday |
| 4 | Friday |
| 5 | Saturday |
| 6 | Sunday |

**Example:**
```json
"active_days": [0, 1, 2, 3, 4]  // Monday to Friday
"active_days": [5, 6]            // Weekends only
"active_days": [0, 1, 2, 3, 4, 5, 6]  // Every day
```

---

## Data Models

### Route Response Schema

```typescript
interface RouteResponse {
  id: string;                           // UUID
  driver_id: string;                    // UUID
  vehicle_id: string;                   // UUID
  name: string;
  departure_time: string;               // HH:MM:SS
  active_days: number[];                // 0-6 (Mon-Sun)
  seats_total: number;
  seats_available: number;
  base_price: number;                   // Decimal (NGN)
  schedule_rrule: string | null;
  notes: string | null;
  status: RouteStatus;
  stops: RouteStopResponse[];
  created_at: string;                   // ISO 8601
  updated_at: string;                   // ISO 8601
}

interface RouteStopResponse {
  id: string;                           // UUID (route_stop ID)
  stop_id: string;                      // UUID (stop ID)
  stop_order: number;
  stop_name: string;
  stop_lat: number;                     // Decimal
  stop_lon: number;                     // Decimal
  planned_arrival_offset_minutes: number;
  price_from_origin: number;            // Decimal (NGN)
}

type RouteStatus = 'ACTIVE' | 'PAUSED' | 'CANCELLED';
```

---

## Validation Rules

### Stop Validation

1. **Minimum Stops:** At least 2 (origin + destination)
2. **First Stop:** Must have `planned_arrival_offset_minutes = 0`
3. **Arrival Offsets:** Must be strictly increasing
4. **Prices:** Must be non-decreasing from origin
5. **First Stop Price:** Must be 0.00

### Route Validation

1. **Active Days:** Must contain at least 1 day, no duplicates
2. **Seats:** Must be between 1 and 50
3. **Base Price:** Must be non-negative
4. **Name:** Required, 1-255 characters

---

## Rate Limiting

| Action | Limit | Window |
|--------|-------|--------|
| Create Route | 5 requests | Per hour |
| Update Route | 20 requests | Per hour |
| List Routes | 100 requests | Per minute |

---

## Error Handling

### Common Error Responses

| Code | Error | Description |
|------|-------|-------------|
| 400 | Bad Request | Validation error, invalid data |
| 401 | Unauthorized | Invalid or missing token |
| 403 | Forbidden | Not a driver, KYC not verified, not owner |
| 404 | Not Found | Route not found |
| 422 | Unprocessable Entity | Validation failed |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |

---

## Integration Notes for Frontend

### Complete Route Management Flow

```javascript
// 1. Create route
async function createRoute(routeData) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch('http://localhost:8091/routes', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(routeData)
  });
  
  if (!response.ok) {
    const error = await response.json();
    throw new Error(error.detail);
  }
  
  return response.json();
}

// 2. Get driver's routes
async function getMyRoutes(status = null) {
  const token = localStorage.getItem('accessToken');
  const params = new URLSearchParams();
  if (status) params.append('status', status);
  params.append('include_stops', 'true');
  
  const response = await fetch(
    `http://localhost:8091/routes?${params}`,
    {
      headers: { 'Authorization': `Bearer ${token}` }
    }
  );
  return response.json();
}

// 3. Update route status
async function updateRouteStatus(routeId, status) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(
    `http://localhost:8091/routes/${routeId}/status?status=${status}`,
    {
      method: 'PATCH',
      headers: { 'Authorization': `Bearer ${token}` }
    }
  );
  return response.json();
}

// 4. Delete route
async function deleteRoute(routeId) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch(
    `http://localhost:8091/routes/${routeId}`,
    {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${token}` }
    }
  );
  
  if (response.status !== 204) {
    const error = await response.json();
    throw new Error(error.detail);
  }
}
```

### Route Form Builder

```javascript
class RouteFormBuilder {
  constructor() {
    this.stops = [{
      name: '',
      lat: null,
      lon: null,
      address: '',
      planned_arrival_offset_minutes: 0,
      price_from_origin: 0.00
    }];
  }
  
  addStop() {
    const lastStop = this.stops[this.stops.length - 1];
    this.stops.push({
      name: '',
      lat: null,
      lon: null,
      address: '',
      planned_arrival_offset_minutes: lastStop.planned_arrival_offset_minutes + 30,
      price_from_origin: lastStop.price_from_origin + 500
    });
  }
  
  removeStop(index) {
    if (this.stops.length > 2) {
      this.stops.splice(index, 1);
    }
  }
  
  validate() {
    const errors = [];
    
    // Check minimum stops
    if (this.stops.length < 2) {
      errors.push('Route must have at least 2 stops');
    }
    
    // Check first stop
    if (this.stops[0].planned_arrival_offset_minutes !== 0) {
      errors.push('First stop must have 0 minutes offset');
    }
    
    if (this.stops[0].price_from_origin !== 0) {
      errors.push('First stop must have 0 price');
    }
    
    // Check offsets are increasing
    for (let i = 1; i < this.stops.length; i++) {
      if (this.stops[i].planned_arrival_offset_minutes <= 
          this.stops[i-1].planned_arrival_offset_minutes) {
        errors.push(`Stop ${i+1} offset must be greater than Stop ${i}`);
      }
    }
    
    // Check prices are non-decreasing
    for (let i = 1; i < this.stops.length; i++) {
      if (this.stops[i].price_from_origin < 
          this.stops[i-1].price_from_origin) {
        errors.push(`Stop ${i+1} price cannot be less than Stop ${i}`);
      }
    }
    
    return errors;
  }
  
  build() {
    const errors = this.validate();
    if (errors.length > 0) {
      throw new Error(errors.join('; '));
    }
    return { stops: this.stops };
  }
}
```

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-21 | Initial API documentation |

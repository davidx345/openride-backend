# Search Service API Documentation

## Service Information
- **Service Name:** Search Service
- **Port:** 8093
- **Base URL:** `/v1`
- **Technology:** Python / FastAPI
- **Description:** Public route discovery service enabling riders to search, browse, and explore available routes with advanced geospatial and temporal filtering

---

## Authentication

Most endpoints require JWT authentication. Some public endpoints (marked as PUBLIC) do not require authentication.

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

### 1. Search Routes

Search for available routes based on location, time, and filters.

**Endpoint:** `POST /search`

**Authentication:** PUBLIC (optional authentication for personalized results)

**Request Body:**
```json
{
  "query": "Lagos to Ibadan",
  "origin_lat": 6.5833,
  "origin_lon": 3.3833,
  "destination_lat": 7.3833,
  "destination_lon": 3.9000,
  "date": "2025-11-25",
  "radius_km": 5,
  "time_window": "morning",
  "min_seats": 1,
  "max_price": 5000.00,
  "filters": {
    "ac_only": true,
    "min_rating": 4.0,
    "verified_drivers": true
  },
  "sort_by": "price_asc",
  "page": 1,
  "page_size": 20
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| query | string | No | Max 255 chars | Text search (route name, locations) |
| origin_lat | decimal | No | -90 to 90 | Origin latitude |
| origin_lon | decimal | No | -180 to 180 | Origin longitude |
| destination_lat | decimal | No | -90 to 90 | Destination latitude |
| destination_lon | decimal | No | -180 to 180 | Destination longitude |
| date | date | No | YYYY-MM-DD | Travel date filter |
| radius_km | decimal | No | 0.1-50 | Search radius (default: 5) |
| time_window | string | No | Enum | morning, afternoon, evening, night |
| min_seats | integer | No | 1-50 | Minimum available seats |
| max_price | decimal | No | ≥ 0 | Maximum price filter |
| filters | object | No | - | Additional filters |
| sort_by | string | No | Enum | Sorting option |
| page | integer | No | ≥ 1 | Page number (default: 1) |
| page_size | integer | No | 1-100 | Results per page (default: 20) |

**Filter Options:**

| Field | Type | Description |
|-------|------|-------------|
| ac_only | boolean | AC vehicles only |
| min_rating | decimal | Minimum driver rating (1.0-5.0) |
| verified_drivers | boolean | Verified drivers only |
| female_driver | boolean | Female drivers only |
| wifi_available | boolean | WiFi available |

**Sort Options:**

| Value | Description |
|-------|-------------|
| price_asc | Lowest price first |
| price_desc | Highest price first |
| departure_asc | Earliest departure first |
| departure_desc | Latest departure first |
| rating_desc | Highest rating first |
| distance_asc | Closest first |

**Success Response (200 OK):**
```json
{
  "results": [
    {
      "route_id": "456e4567-e89b-12d3-a456-426614174001",
      "name": "Lagos - Ibadan Express",
      "driver": {
        "id": "234e4567-e89b-12d3-a456-426614174001",
        "name": "John Doe",
        "rating": 4.8,
        "completed_trips": 450,
        "is_verified": true
      },
      "vehicle": {
        "make": "Toyota",
        "model": "Hiace",
        "year": 2020,
        "plate_number": "LAG-123-XY",
        "features": ["AC", "WiFi", "USB Charging"]
      },
      "schedule": {
        "departure_time": "08:00:00",
        "active_days": [0, 1, 2, 3, 4],
        "next_available": "2025-11-25T08:00:00Z"
      },
      "origin": {
        "name": "Ojota Bus Stop",
        "lat": 6.5833,
        "lon": 3.3833,
        "distance_km": 0.15
      },
      "destination": {
        "name": "Challenge Bus Stop",
        "lat": 7.3833,
        "lon": 3.9000,
        "distance_km": 0.20
      },
      "pricing": {
        "base_price": 3500.00,
        "platform_fee": 350.00,
        "total": 3850.00,
        "currency": "NGN"
      },
      "availability": {
        "seats_total": 14,
        "seats_available": 10
      },
      "journey": {
        "distance_km": 125.5,
        "estimated_duration_minutes": 90
      }
    }
  ],
  "pagination": {
    "page": 1,
    "page_size": 20,
    "total_results": 1,
    "total_pages": 1
  },
  "filters_applied": {
    "date": "2025-11-25",
    "time_window": "morning",
    "radius_km": 5,
    "ac_only": true,
    "min_rating": 4.0
  }
}
```

**Error Responses:**

**400 Bad Request:**
```json
{
  "detail": "Either query text or origin/destination coordinates required"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8093/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Lagos to Ibadan",
    "date": "2025-11-25",
    "time_window": "morning",
    "filters": {
      "ac_only": true,
      "min_rating": 4.0
    },
    "sort_by": "price_asc"
  }'
```

---

### 2. Get Route Details

Get detailed information about a specific route.

**Endpoint:** `GET /routes/{route_id}`

**Authentication:** PUBLIC

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| route_id | UUID | Yes | Route ID |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| include_stops | boolean | No | true | Include all stop details |
| include_reviews | boolean | No | false | Include driver reviews |
| date | date | No | null | Check availability for date |

**Success Response (200 OK):**
```json
{
  "id": "456e4567-e89b-12d3-a456-426614174001",
  "name": "Lagos - Ibadan Express",
  "status": "ACTIVE",
  "driver": {
    "id": "234e4567-e89b-12d3-a456-426614174001",
    "name": "John Doe",
    "rating": 4.8,
    "completed_trips": 450,
    "is_verified": true,
    "member_since": "2023-01-15",
    "bio": "Professional driver with 10 years experience"
  },
  "vehicle": {
    "make": "Toyota",
    "model": "Hiace",
    "year": 2020,
    "color": "White",
    "plate_number": "LAG-123-XY",
    "features": ["AC", "WiFi", "USB Charging", "Entertainment System"]
  },
  "schedule": {
    "departure_time": "08:00:00",
    "active_days": [0, 1, 2, 3, 4],
    "schedule_rrule": "FREQ=DAILY;BYDAY=MO,TU,WE,TH,FR",
    "next_available": "2025-11-25T08:00:00Z"
  },
  "stops": [
    {
      "id": "678e4567-e89b-12d3-a456-426614174003",
      "name": "Ojota Bus Stop",
      "lat": 6.5833,
      "lon": 3.3833,
      "address": "Ojota, Lagos State",
      "landmark": "Near Ojota Police Station",
      "order": 0,
      "eta_from_start_minutes": 0,
      "price_from_origin": 0.00
    },
    {
      "id": "678e4567-e89b-12d3-a456-426614174005",
      "name": "Challenge Bus Stop",
      "lat": 7.3833,
      "lon": 3.9000,
      "address": "Challenge, Ibadan, Oyo State",
      "landmark": "Challenge Roundabout",
      "order": 1,
      "eta_from_start_minutes": 90,
      "price_from_origin": 3500.00
    }
  ],
  "pricing": {
    "base_price": 3500.00,
    "platform_fee_percent": 10,
    "currency": "NGN"
  },
  "availability": {
    "seats_total": 14,
    "seats_available": 10,
    "date_checked": "2025-11-25"
  },
  "journey": {
    "distance_km": 125.5,
    "estimated_duration_minutes": 90
  },
  "policies": {
    "cancellation_policy": "Free cancellation up to 2 hours before departure",
    "luggage_policy": "1 carry-on + 1 checked bag included"
  },
  "reviews": [
    {
      "id": "789e4567-e89b-12d3-a456-426614174006",
      "rating": 5,
      "comment": "Great driver, very punctual!",
      "rider_name": "Jane D.",
      "created_at": "2025-11-20T14:30:00Z"
    }
  ],
  "stats": {
    "total_bookings": 1250,
    "on_time_rate": 0.96,
    "cancellation_rate": 0.02
  }
}
```

**Error Responses:**

**404 Not Found:**
```json
{
  "detail": "Route not found"
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8093/routes/456e4567-e89b-12d3-a456-426614174001?include_reviews=true&date=2025-11-25"
```

---

### 3. Browse Popular Routes

Get popular or trending routes.

**Endpoint:** `GET /routes/popular`

**Authentication:** PUBLIC

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| region | string | No | null | Filter by region (Lagos, Ibadan, etc.) |
| time_range | string | No | 7d | 24h, 7d, 30d |
| limit | integer | No | 10 | Max results (1-50) |

**Success Response (200 OK):**
```json
{
  "popular_routes": [
    {
      "route_id": "456e4567-e89b-12d3-a456-426614174001",
      "name": "Lagos - Ibadan Express",
      "origin": "Lagos",
      "destination": "Ibadan",
      "booking_count": 450,
      "average_rating": 4.8,
      "average_price": 3850.00,
      "thumbnail_url": "https://cdn.example.com/routes/lag-iba.jpg"
    }
  ],
  "metadata": {
    "time_range": "7d",
    "region": null,
    "generated_at": "2025-11-21T10:00:00Z"
  }
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8093/routes/popular?region=Lagos&time_range=7d&limit=10"
```

---

### 4. Search by Location (Nearby Routes)

Find routes near a specific location.

**Endpoint:** `GET /routes/nearby`

**Authentication:** PUBLIC

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| lat | decimal | Yes | - | Latitude |
| lon | decimal | Yes | - | Longitude |
| radius_km | decimal | No | 5 | Search radius (0.1-50) |
| type | string | No | both | origin, destination, both |
| limit | integer | No | 20 | Max results (1-100) |

**Success Response (200 OK):**
```json
{
  "routes": [
    {
      "route_id": "456e4567-e89b-12d3-a456-426614174001",
      "name": "Lagos - Ibadan Express",
      "matched_stop": {
        "name": "Ojota Bus Stop",
        "lat": 6.5833,
        "lon": 3.3833,
        "distance_km": 0.15,
        "type": "origin"
      },
      "departure_time": "08:00:00",
      "base_price": 3500.00,
      "seats_available": 10
    }
  ],
  "search_point": {
    "lat": 6.5833,
    "lon": 3.3833
  },
  "radius_km": 5,
  "total_results": 1
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8093/routes/nearby?lat=6.5833&lon=3.3833&radius_km=5&type=origin&limit=20"
```

---

### 5. Autocomplete Location

Get location suggestions for search.

**Endpoint:** `GET /locations/autocomplete`

**Authentication:** PUBLIC

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| query | string | Yes | - | Search query (min 2 chars) |
| limit | integer | No | 10 | Max results (1-20) |
| types | string | No | all | stop, city, landmark |

**Success Response (200 OK):**
```json
{
  "suggestions": [
    {
      "id": "678e4567-e89b-12d3-a456-426614174003",
      "name": "Ojota Bus Stop",
      "address": "Ojota, Lagos State",
      "lat": 6.5833,
      "lon": 3.3833,
      "type": "stop",
      "relevance_score": 0.95
    },
    {
      "id": null,
      "name": "Lagos",
      "address": "Lagos State, Nigeria",
      "lat": 6.5244,
      "lon": 3.3792,
      "type": "city",
      "relevance_score": 0.85
    }
  ],
  "query": "Ojota",
  "total_results": 2
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8093/locations/autocomplete?query=Ojota&limit=10"
```

---

### 6. Get Schedule for Route

Get upcoming schedule instances for a route.

**Endpoint:** `GET /routes/{route_id}/schedule`

**Authentication:** PUBLIC

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| route_id | UUID | Yes | Route ID |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| start_date | date | No | today | Start date (YYYY-MM-DD) |
| end_date | date | No | +30 days | End date (YYYY-MM-DD) |
| include_availability | boolean | No | true | Include seat availability |

**Success Response (200 OK):**
```json
{
  "route_id": "456e4567-e89b-12d3-a456-426614174001",
  "route_name": "Lagos - Ibadan Express",
  "schedule_instances": [
    {
      "date": "2025-11-25",
      "departure_time": "08:00:00",
      "departure_datetime": "2025-11-25T08:00:00Z",
      "day_of_week": "Monday",
      "seats_available": 10,
      "seats_total": 14,
      "status": "available"
    },
    {
      "date": "2025-11-26",
      "departure_time": "08:00:00",
      "departure_datetime": "2025-11-26T08:00:00Z",
      "day_of_week": "Tuesday",
      "seats_available": 14,
      "seats_total": 14,
      "status": "available"
    }
  ],
  "date_range": {
    "start": "2025-11-25",
    "end": "2025-12-25"
  },
  "total_instances": 22
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8093/routes/456e4567-e89b-12d3-a456-426614174001/schedule?start_date=2025-11-25&end_date=2025-12-25"
```

---

### 7. Get Route Statistics

Get public statistics for a route.

**Endpoint:** `GET /routes/{route_id}/stats`

**Authentication:** PUBLIC

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| route_id | UUID | Yes | Route ID |

**Success Response (200 OK):**
```json
{
  "route_id": "456e4567-e89b-12d3-a456-426614174001",
  "total_bookings": 1250,
  "total_trips_completed": 1180,
  "on_time_rate": 0.96,
  "cancellation_rate": 0.02,
  "average_rating": 4.8,
  "total_reviews": 450,
  "rating_distribution": {
    "5": 350,
    "4": 80,
    "3": 15,
    "2": 3,
    "1": 2
  },
  "busiest_days": ["Monday", "Friday"],
  "peak_booking_hours": [7, 8, 17, 18]
}
```

**Example cURL:**
```bash
curl -X GET http://localhost:8093/routes/456e4567-e89b-12d3-a456-426614174001/stats
```

---

## Data Models

### Search Result Schema

```typescript
interface SearchResponse {
  results: RouteSearchResult[];
  pagination: PaginationInfo;
  filters_applied: FiltersApplied;
}

interface RouteSearchResult {
  route_id: string;                    // UUID
  name: string;
  driver: DriverInfo;
  vehicle: VehicleInfo;
  schedule: ScheduleInfo;
  origin: LocationInfo;
  destination: LocationInfo;
  pricing: PricingInfo;
  availability: AvailabilityInfo;
  journey: JourneyInfo;
}

interface DriverInfo {
  id: string;                          // UUID
  name: string;
  rating: number;                      // 1.0-5.0
  completed_trips: number;
  is_verified: boolean;
}

interface VehicleInfo {
  make: string;
  model: string;
  year: number;
  plate_number: string;
  features: string[];
}

interface ScheduleInfo {
  departure_time: string;              // HH:MM:SS
  active_days: number[];               // 0-6
  next_available: string;              // ISO 8601
}

interface LocationInfo {
  name: string;
  lat: number;
  lon: number;
  distance_km: number;                 // Distance from search point
}

interface PricingInfo {
  base_price: number;
  platform_fee: number;
  total: number;
  currency: string;                    // NGN
}

interface AvailabilityInfo {
  seats_total: number;
  seats_available: number;
}

interface JourneyInfo {
  distance_km: number;
  estimated_duration_minutes: number;
}

interface PaginationInfo {
  page: number;
  page_size: number;
  total_results: number;
  total_pages: number;
}
```

---

## Rate Limiting

| Action | Limit | Window |
|--------|-------|--------|
| Search Routes | 60 requests | Per minute |
| Get Route Details | 120 requests | Per minute |
| Autocomplete | 100 requests | Per minute |
| Popular Routes | 30 requests | Per minute |

---

## Error Handling

### Common Error Responses

| Code | Error | Description |
|------|-------|-------------|
| 400 | Bad Request | Invalid parameters |
| 404 | Not Found | Route not found |
| 422 | Unprocessable Entity | Validation failed |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |

---

## Integration Notes for Frontend

### Complete Search Flow

```javascript
// 1. Basic search
async function searchRoutes(searchParams) {
  const response = await fetch('http://localhost:8093/search', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(searchParams)
  });
  return response.json();
}

// 2. Get route details
async function getRouteDetails(routeId, date = null) {
  const params = new URLSearchParams();
  if (date) params.append('date', date);
  params.append('include_stops', 'true');
  params.append('include_reviews', 'true');
  
  const response = await fetch(
    `http://localhost:8093/routes/${routeId}?${params}`
  );
  return response.json();
}

// 3. Location autocomplete
async function autocompleteLocation(query) {
  const params = new URLSearchParams({ query, limit: '10' });
  const response = await fetch(
    `http://localhost:8093/locations/autocomplete?${params}`
  );
  return response.json();
}

// 4. Get nearby routes
async function getNearbyRoutes(lat, lon, radiusKm = 5) {
  const params = new URLSearchParams({
    lat: lat.toString(),
    lon: lon.toString(),
    radius_km: radiusKm.toString(),
    type: 'both',
    limit: '20'
  });
  
  const response = await fetch(
    `http://localhost:8093/routes/nearby?${params}`
  );
  return response.json();
}
```

### Search Form with Autocomplete

```javascript
class LocationSearchInput {
  constructor(inputElement) {
    this.input = inputElement;
    this.suggestions = [];
    this.debounceTimer = null;
    
    this.input.addEventListener('input', (e) => {
      this.handleInput(e.target.value);
    });
  }
  
  handleInput(query) {
    clearTimeout(this.debounceTimer);
    
    if (query.length < 2) {
      this.hideSuggestions();
      return;
    }
    
    this.debounceTimer = setTimeout(async () => {
      this.suggestions = await autocompleteLocation(query);
      this.showSuggestions();
    }, 300);
  }
  
  showSuggestions() {
    // Display suggestions UI
    const list = this.suggestions.suggestions.map(s => ({
      id: s.id,
      label: s.name,
      sublabel: s.address,
      lat: s.lat,
      lon: s.lon,
      type: s.type
    }));
    
    // Render list...
  }
  
  selectSuggestion(suggestion) {
    this.input.value = suggestion.label;
    this.onSelect(suggestion);
    this.hideSuggestions();
  }
}
```

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-21 | Initial API documentation |

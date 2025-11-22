# Matchmaking Service API Documentation

## Service Information
- **Service Name:** Matchmaking Service
- **Port:** 8092
- **Base URL:** `/v1`
- **Technology:** Python / FastAPI
- **Description:** Intelligent route matching algorithm that connects riders with suitable driver routes based on origin, destination, time, and preferences

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

---

## Endpoints

### 1. Find Matching Routes

Find routes that match rider's origin, destination, and time preferences.

**Endpoint:** `POST /match`

**Authentication:** Required (RIDER role)

**Request Body:**
```json
{
  "origin_lat": 6.5833,
  "origin_lon": 3.3833,
  "origin_name": "Ojota Bus Stop",
  "destination_lat": 7.3833,
  "destination_lon": 3.9000,
  "destination_name": "Challenge Bus Stop",
  "departure_date": "2025-11-25",
  "departure_time_preference": "morning",
  "max_wait_minutes": 30,
  "max_detour_minutes": 15,
  "seats_needed": 1,
  "max_price": 5000.00,
  "preferences": {
    "ac_required": true,
    "wifi_preferred": false,
    "female_driver_only": false,
    "min_rating": 4.0
  }
}
```

**Request Fields:**

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| origin_lat | decimal | Yes | -90 to 90 | Origin latitude |
| origin_lon | decimal | Yes | -180 to 180 | Origin longitude |
| origin_name | string | No | Max 255 chars | Origin stop name |
| destination_lat | decimal | Yes | -90 to 90 | Destination latitude |
| destination_lon | decimal | Yes | -180 to 180 | Destination longitude |
| destination_name | string | No | Max 255 chars | Destination stop name |
| departure_date | date | Yes | YYYY-MM-DD | Desired travel date |
| departure_time_preference | string | No | Enum | morning, afternoon, evening, night |
| max_wait_minutes | integer | No | 0-120 | Max wait time at origin |
| max_detour_minutes | integer | No | 0-60 | Max detour tolerance |
| seats_needed | integer | No | 1-10 | Default: 1 |
| max_price | decimal | No | ≥ 0 | Maximum willing to pay |
| preferences | object | No | - | Additional preferences |

**Preference Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| ac_required | boolean | No | Air conditioning required |
| wifi_preferred | boolean | No | WiFi preferred |
| female_driver_only | boolean | No | Female driver only |
| min_rating | decimal | No | Minimum driver rating (1.0-5.0) |

**Time Preferences:**

| Value | Time Range |
|-------|------------|
| morning | 05:00 - 11:59 |
| afternoon | 12:00 - 16:59 |
| evening | 17:00 - 20:59 |
| night | 21:00 - 04:59 |

**Success Response (200 OK):**
```json
{
  "matches": [
    {
      "route_id": "456e4567-e89b-12d3-a456-426614174001",
      "match_score": 98.5,
      "driver": {
        "id": "234e4567-e89b-12d3-a456-426614174001",
        "name": "John Doe",
        "rating": 4.8,
        "completed_trips": 450,
        "vehicle": {
          "make": "Toyota",
          "model": "Hiace",
          "year": 2020,
          "plate_number": "LAG-123-XY",
          "features": ["AC", "USB Charging"]
        }
      },
      "route": {
        "name": "Lagos - Ibadan Express",
        "departure_time": "08:00:00",
        "seats_available": 10
      },
      "pickup": {
        "stop_id": "678e4567-e89b-12d3-a456-426614174003",
        "name": "Ojota Bus Stop",
        "lat": 6.5833,
        "lon": 3.3833,
        "distance_meters": 150,
        "eta_minutes": 2,
        "scheduled_time": "08:00:00"
      },
      "dropoff": {
        "stop_id": "678e4567-e89b-12d3-a456-426614174005",
        "name": "Challenge Bus Stop",
        "lat": 7.3833,
        "lon": 3.9000,
        "distance_meters": 200,
        "eta_minutes": 90,
        "scheduled_time": "09:30:00"
      },
      "pricing": {
        "base_fare": 3500.00,
        "platform_fee": 350.00,
        "total": 3850.00,
        "currency": "NGN"
      },
      "journey": {
        "distance_km": 125.5,
        "estimated_duration_minutes": 90,
        "detour_minutes": 0
      },
      "score_breakdown": {
        "proximity_score": 100.0,
        "time_match_score": 95.0,
        "price_score": 100.0,
        "rating_score": 96.0,
        "availability_score": 100.0
      }
    }
  ],
  "total_matches": 1,
  "search_params": {
    "origin": "Ojota Bus Stop",
    "destination": "Challenge Bus Stop",
    "date": "2025-11-25"
  }
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| matches | array | Matching routes sorted by score |
| total_matches | integer | Total number of matches found |
| search_params | object | Echo of search parameters |

**Match Object Fields:**

| Field | Type | Description |
|-------|------|-------------|
| route_id | UUID | Route identifier |
| match_score | decimal | Overall match score (0-100) |
| driver | object | Driver information |
| route | object | Route details |
| pickup | object | Pickup stop details |
| dropoff | object | Drop-off stop details |
| pricing | object | Fare breakdown |
| journey | object | Journey details |
| score_breakdown | object | Detailed scoring |

**Score Breakdown:**

| Score | Weight | Description |
|-------|--------|-------------|
| proximity_score | 30% | How close stops are to origin/destination |
| time_match_score | 25% | How well time matches preference |
| price_score | 20% | Price competitiveness |
| rating_score | 15% | Driver rating |
| availability_score | 10% | Seat availability |

**Error Responses:**

**400 Bad Request:**
```json
{
  "detail": "Origin and destination cannot be the same"
}
```

**404 Not Found:**
```json
{
  "detail": "No matching routes found for your criteria"
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8092/match \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "origin_lat": 6.5833,
    "origin_lon": 3.3833,
    "destination_lat": 7.3833,
    "destination_lon": 3.9000,
    "departure_date": "2025-11-25",
    "seats_needed": 1
  }'
```

---

### 2. Get Match Details

Get detailed information about a specific match.

**Endpoint:** `GET /match/{route_id}`

**Authentication:** Required (RIDER role)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| route_id | UUID | Yes | Route ID from match results |

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| origin_lat | decimal | Yes | Origin latitude |
| origin_lon | decimal | Yes | Origin longitude |
| destination_lat | decimal | Yes | Destination latitude |
| destination_lon | decimal | Yes | Destination longitude |
| departure_date | date | Yes | Travel date (YYYY-MM-DD) |

**Success Response (200 OK):**

Returns single match object (same structure as match in Find Matching Routes).

**Error Responses:**

**404 Not Found:**
```json
{
  "detail": "Route not found or not available"
}
```

**Example cURL:**
```bash
curl -X GET "http://localhost:8092/match/456e4567-e89b-12d3-a456-426614174001?origin_lat=6.5833&origin_lon=3.3833&destination_lat=7.3833&destination_lon=3.9000&departure_date=2025-11-25" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

---

### 3. Calculate Match Score (Internal)

Calculate match score for a specific route and criteria. Used internally but exposed for testing.

**Endpoint:** `POST /match/score`

**Authentication:** Required

**Request Body:**
```json
{
  "route_id": "456e4567-e89b-12d3-a456-426614174001",
  "origin_lat": 6.5833,
  "origin_lon": 3.3833,
  "destination_lat": 7.3833,
  "destination_lon": 3.9000,
  "departure_date": "2025-11-25",
  "departure_time_preference": "morning",
  "max_price": 5000.00
}
```

**Success Response (200 OK):**
```json
{
  "route_id": "456e4567-e89b-12d3-a456-426614174001",
  "total_score": 98.5,
  "score_breakdown": {
    "proximity_score": 100.0,
    "time_match_score": 95.0,
    "price_score": 100.0,
    "rating_score": 96.0,
    "availability_score": 100.0
  },
  "weights": {
    "proximity": 0.30,
    "time": 0.25,
    "price": 0.20,
    "rating": 0.15,
    "availability": 0.10
  }
}
```

**Example cURL:**
```bash
curl -X POST http://localhost:8092/match/score \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "route_id": "456e4567-e89b-12d3-a456-426614174001",
    "origin_lat": 6.5833,
    "origin_lon": 3.3833,
    "destination_lat": 7.3833,
    "destination_lon": 3.9000,
    "departure_date": "2025-11-25"
  }'
```

---

## Matching Algorithm

### Algorithm Overview

The matchmaking algorithm uses a weighted scoring system to rank routes:

```
total_score = (proximity_score × 0.30) + 
              (time_match_score × 0.25) + 
              (price_score × 0.20) + 
              (rating_score × 0.15) + 
              (availability_score × 0.10)
```

### Proximity Score

Calculates how close route stops are to origin/destination:

```python
def proximity_score(origin_distance, destination_distance):
    max_acceptable_distance = 1000  # meters
    
    origin_score = max(0, 100 - (origin_distance / max_acceptable_distance * 100))
    dest_score = max(0, 100 - (destination_distance / max_acceptable_distance * 100))
    
    return (origin_score + dest_score) / 2
```

**Distance Penalty:**
- 0-100m: 100 points
- 100-500m: 95-80 points
- 500-1000m: 80-50 points
- 1000m+: 0 points (filtered out)

### Time Match Score

Matches departure time with preference:

```python
def time_match_score(route_time, preference, max_wait_minutes):
    # Check if route time falls within preference window
    if not in_preference_window(route_time, preference):
        return 50  # Penalty for wrong time window
    
    # Calculate wait time score
    wait_score = max(0, 100 - (wait_minutes / max_wait_minutes * 50))
    
    return wait_score
```

### Price Score

Evaluates price competitiveness:

```python
def price_score(route_price, max_price, market_average):
    if route_price > max_price:
        return 0  # Filter out
    
    # Reward lower prices
    price_ratio = route_price / market_average
    return max(0, 100 - (price_ratio - 0.8) * 200)
```

### Rating Score

Driver rating contribution:

```python
def rating_score(driver_rating):
    # Linear scale: 5.0 = 100, 1.0 = 0
    return (driver_rating - 1.0) / 4.0 * 100
```

### Availability Score

Seat availability:

```python
def availability_score(seats_available, seats_needed):
    if seats_available < seats_needed:
        return 0  # Filter out
    
    # Higher availability = better score
    availability_ratio = seats_available / seats_total
    return min(100, availability_ratio * 150)
```

---

## Filtering Rules

Routes are filtered out (not returned) if they fail these criteria:

| Criteria | Threshold | Description |
|----------|-----------|-------------|
| Origin Distance | > 1000m | Pickup too far |
| Destination Distance | > 1000m | Drop-off too far |
| Seat Availability | < seats_needed | Not enough seats |
| Price | > max_price | Too expensive |
| Date | Past date | Cannot travel in past |
| Route Status | Not ACTIVE | Route not active |
| Driver Rating | < min_rating | Rating too low |
| Wait Time | > max_wait_minutes | Wait too long |

---

## Data Models

### Match Request Schema

```typescript
interface MatchRequest {
  origin_lat: number;
  origin_lon: number;
  origin_name?: string;
  destination_lat: number;
  destination_lon: number;
  destination_name?: string;
  departure_date: string;              // YYYY-MM-DD
  departure_time_preference?: 'morning' | 'afternoon' | 'evening' | 'night';
  max_wait_minutes?: number;           // Default: 30
  max_detour_minutes?: number;         // Default: 15
  seats_needed?: number;               // Default: 1
  max_price?: number;
  preferences?: MatchPreferences;
}

interface MatchPreferences {
  ac_required?: boolean;
  wifi_preferred?: boolean;
  female_driver_only?: boolean;
  min_rating?: number;                 // 1.0-5.0
}
```

### Match Response Schema

```typescript
interface MatchResponse {
  matches: RouteMatch[];
  total_matches: number;
  search_params: SearchParams;
}

interface RouteMatch {
  route_id: string;                    // UUID
  match_score: number;                 // 0-100
  driver: DriverInfo;
  route: RouteInfo;
  pickup: StopInfo;
  dropoff: StopInfo;
  pricing: PricingInfo;
  journey: JourneyInfo;
  score_breakdown: ScoreBreakdown;
}

interface DriverInfo {
  id: string;                          // UUID
  name: string;
  rating: number;                      // 1.0-5.0
  completed_trips: number;
  vehicle: VehicleInfo;
}

interface VehicleInfo {
  make: string;
  model: string;
  year: number;
  plate_number: string;
  features: string[];
}

interface RouteInfo {
  name: string;
  departure_time: string;              // HH:MM:SS
  seats_available: number;
}

interface StopInfo {
  stop_id: string;                     // UUID
  name: string;
  lat: number;
  lon: number;
  distance_meters: number;             // Distance from user's point
  eta_minutes: number;
  scheduled_time: string;              // HH:MM:SS
}

interface PricingInfo {
  base_fare: number;
  platform_fee: number;
  total: number;
  currency: string;                    // NGN
}

interface JourneyInfo {
  distance_km: number;
  estimated_duration_minutes: number;
  detour_minutes: number;
}

interface ScoreBreakdown {
  proximity_score: number;             // 0-100
  time_match_score: number;            // 0-100
  price_score: number;                 // 0-100
  rating_score: number;                // 0-100
  availability_score: number;          // 0-100
}
```

---

## Geospatial Calculations

### Distance Calculation

Uses Haversine formula for calculating distances:

```python
from math import radians, cos, sin, asin, sqrt

def haversine_distance(lat1, lon1, lat2, lon2):
    """
    Calculate distance between two points in meters
    """
    R = 6371000  # Earth radius in meters
    
    lat1, lon1, lat2, lon2 = map(radians, [lat1, lon1, lat2, lon2])
    dlat = lat2 - lat1
    dlon = lon2 - lon1
    
    a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
    c = 2 * asin(sqrt(a))
    
    return R * c
```

### Nearest Stop Finding

Finds the nearest stop on a route to a given point:

```python
def find_nearest_stop(lat, lon, route_stops):
    """
    Find nearest stop to given coordinates
    Returns (stop, distance_meters)
    """
    nearest = None
    min_distance = float('inf')
    
    for stop in route_stops:
        distance = haversine_distance(lat, lon, stop.lat, stop.lon)
        if distance < min_distance:
            min_distance = distance
            nearest = stop
    
    return nearest, min_distance
```

---

## Rate Limiting

| Action | Limit | Window |
|--------|-------|--------|
| Match Search | 20 requests | Per minute |
| Get Match Details | 100 requests | Per minute |

---

## Error Handling

### Common Error Responses

| Code | Error | Description |
|------|-------|-------------|
| 400 | Bad Request | Invalid coordinates, dates, or parameters |
| 401 | Unauthorized | Invalid or missing token |
| 404 | Not Found | No matching routes found |
| 422 | Unprocessable Entity | Validation failed |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |

---

## Integration Notes for Frontend

### Complete Matching Flow

```javascript
// 1. Search for matching routes
async function findMatches(searchParams) {
  const token = localStorage.getItem('accessToken');
  const response = await fetch('http://localhost:8092/match', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(searchParams)
  });
  
  if (!response.ok) {
    if (response.status === 404) {
      return { matches: [], total_matches: 0 };
    }
    const error = await response.json();
    throw new Error(error.detail);
  }
  
  return response.json();
}

// 2. Get detailed match info
async function getMatchDetails(routeId, searchParams) {
  const token = localStorage.getItem('accessToken');
  const params = new URLSearchParams({
    origin_lat: searchParams.origin_lat,
    origin_lon: searchParams.origin_lon,
    destination_lat: searchParams.destination_lat,
    destination_lon: searchParams.destination_lon,
    departure_date: searchParams.departure_date
  });
  
  const response = await fetch(
    `http://localhost:8092/match/${routeId}?${params}`,
    {
      headers: { 'Authorization': `Bearer ${token}` }
    }
  );
  return response.json();
}
```

### Search Form Example

```javascript
class RouteSearchForm {
  constructor() {
    this.searchParams = {
      origin_lat: null,
      origin_lon: null,
      destination_lat: null,
      destination_lon: null,
      departure_date: this.getTodayDate(),
      seats_needed: 1,
      preferences: {}
    };
  }
  
  getTodayDate() {
    return new Date().toISOString().split('T')[0];
  }
  
  setOrigin(lat, lon, name = null) {
    this.searchParams.origin_lat = lat;
    this.searchParams.origin_lon = lon;
    if (name) this.searchParams.origin_name = name;
  }
  
  setDestination(lat, lon, name = null) {
    this.searchParams.destination_lat = lat;
    this.searchParams.destination_lon = lon;
    if (name) this.searchParams.destination_name = name;
  }
  
  setDate(date) {
    // Validate date is not in past
    const selected = new Date(date);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    
    if (selected < today) {
      throw new Error('Cannot search for past dates');
    }
    
    this.searchParams.departure_date = date;
  }
  
  setTimePreference(preference) {
    const valid = ['morning', 'afternoon', 'evening', 'night'];
    if (!valid.includes(preference)) {
      throw new Error(`Invalid time preference: ${preference}`);
    }
    this.searchParams.departure_time_preference = preference;
  }
  
  setSeatsNeeded(seats) {
    if (seats < 1 || seats > 10) {
      throw new Error('Seats needed must be between 1 and 10');
    }
    this.searchParams.seats_needed = seats;
  }
  
  setMaxPrice(price) {
    if (price < 0) {
      throw new Error('Max price must be non-negative');
    }
    this.searchParams.max_price = price;
  }
  
  setPreference(key, value) {
    this.searchParams.preferences[key] = value;
  }
  
  async search() {
    return findMatches(this.searchParams);
  }
}
```

### Match Results Display

```javascript
function displayMatches(matches) {
  return matches.map(match => ({
    id: match.route_id,
    score: match.match_score,
    
    // Driver info
    driverName: match.driver.name,
    driverRating: match.driver.rating,
    completedTrips: match.driver.completed_trips,
    vehicleInfo: `${match.driver.vehicle.make} ${match.driver.vehicle.model}`,
    
    // Route info
    routeName: match.route.name,
    departureTime: match.route.departure_time,
    seatsAvailable: match.route.seats_available,
    
    // Pickup/Dropoff
    pickupLocation: match.pickup.name,
    pickupDistance: `${match.pickup.distance_meters}m away`,
    pickupTime: match.pickup.scheduled_time,
    
    dropoffLocation: match.dropoff.name,
    dropoffDistance: `${match.dropoff.distance_meters}m away`,
    dropoffTime: match.dropoff.scheduled_time,
    
    // Pricing
    totalPrice: `₦${match.pricing.total.toFixed(2)}`,
    baseFare: match.pricing.base_fare,
    platformFee: match.pricing.platform_fee,
    
    // Journey
    distance: `${match.journey.distance_km.toFixed(1)} km`,
    duration: `${match.journey.estimated_duration_minutes} min`,
    
    // Scores
    scores: match.score_breakdown
  }));
}
```

---

## Testing

### Sample Test Cases

```javascript
// Test 1: Basic match search
const basicSearch = {
  origin_lat: 6.5833,
  origin_lon: 3.3833,
  destination_lat: 7.3833,
  destination_lon: 3.9000,
  departure_date: '2025-11-25',
  seats_needed: 1
};

// Test 2: Search with time preference
const morningSearch = {
  ...basicSearch,
  departure_time_preference: 'morning',
  max_wait_minutes: 20
};

// Test 3: Search with price limit
const budgetSearch = {
  ...basicSearch,
  max_price: 4000.00
};

// Test 4: Search with preferences
const preferenceSearch = {
  ...basicSearch,
  preferences: {
    ac_required: true,
    min_rating: 4.5
  }
};
```

---

## Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-11-21 | Initial API documentation |

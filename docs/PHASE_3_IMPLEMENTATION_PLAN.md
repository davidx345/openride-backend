# Phase 3 Implementation Plan - Search, Discovery & Matchmaking

## Overview

**Phase**: 3 - Search, Discovery & Matching Engine  
**Services**: Search & Discovery Service + Matchmaking Service (Python FastAPI)  
**Performance Target**: P95 latency ≤ 200ms  
**Status**: 🚧 IN PROGRESS

---

## Architecture Design

### Service 1: Search & Discovery Service
**Purpose**: Public-facing API for riders to search for available routes based on location and time.

**Responsibilities**:
- Route search by geospatial proximity
- Temporal filtering (departure time windows)
- Route detail retrieval
- Basic filtering and pagination

**Tech Stack**:
- Python 3.11+ / FastAPI 0.104+
- SQLAlchemy 2.0 (async) + asyncpg
- PostGIS for geospatial queries
- Redis for caching
- Shapely for geometric operations

---

### Service 2: Matchmaking Service (AI/ML Core)
**Purpose**: Intelligent route matching and ranking engine.

**Responsibilities**:
- Multi-factor scoring algorithm
- Route-rider compatibility analysis
- Ranking and explanation generation
- Performance optimization (<200ms)

**Tech Stack**:
- Python 3.11+ / FastAPI 0.104+
- SQLAlchemy 2.0 (async)
- PostGIS for geospatial operations
- NumPy for scoring calculations
- Shapely for directionality
- Redis for hot route caching

---

## Database Schema Additions

### Migration: Add Geospatial Data to Routes

```sql
-- Add start/end coordinates to routes table
ALTER TABLE routes 
ADD COLUMN start_lat DECIMAL(10, 8),
ADD COLUMN start_lon DECIMAL(11, 8),
ADD COLUMN end_lat DECIMAL(10, 8),
ADD COLUMN end_lon DECIMAL(11, 8);

-- Add computed geometry columns for performance
ALTER TABLE routes
ADD COLUMN start_location geometry(POINT, 4326),
ADD COLUMN end_location geometry(POINT, 4326);

-- Create spatial indexes
CREATE INDEX idx_routes_start_location ON routes USING GIST (start_location);
CREATE INDEX idx_routes_end_location ON routes USING GIST (end_location);

-- Add function to auto-update geometry columns
CREATE OR REPLACE FUNCTION update_route_geometries()
RETURNS TRIGGER AS $$
BEGIN
    NEW.start_location = ST_SetSRID(ST_MakePoint(NEW.start_lon, NEW.start_lat), 4326);
    NEW.end_location = ST_SetSRID(ST_MakePoint(NEW.end_lon, NEW.end_lat), 4326);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_route_geometries
BEFORE INSERT OR UPDATE ON routes
FOR EACH ROW
EXECUTE FUNCTION update_route_geometries();
```

---

## Core Algorithms

### 1. Geospatial Filtering (Candidate Retrieval)

**Goal**: Find routes with stops near rider's origin/destination

```python
def get_nearby_routes(
    origin_lat: float, 
    origin_lon: float,
    dest_lat: float,
    dest_lon: float,
    radius_km: float = 5.0
) -> List[Route]:
    """
    Find routes with stops within radius of origin OR destination.
    Uses PostGIS ST_DWithin for efficient spatial queries.
    """
    # Query uses spatial index for performance
    # Returns routes where any stop is within radius
```

**SQL Query Pattern**:
```sql
SELECT DISTINCT r.*
FROM routes r
JOIN route_stops rs ON r.id = rs.route_id
JOIN stops s ON rs.stop_id = s.id
WHERE r.status = 'ACTIVE'
  AND (
    ST_DWithin(
      s.location::geography,
      ST_SetSRID(ST_MakePoint(:origin_lon, :origin_lat), 4326)::geography,
      :radius_meters
    )
    OR
    ST_DWithin(
      s.location::geography,
      ST_SetSRID(ST_MakePoint(:dest_lon, :dest_lat), 4326)::geography,
      :radius_meters
    )
  )
```

---

### 2. Temporal Filtering

**Goal**: Match routes departing within acceptable time window

```python
def filter_by_time(
    routes: List[Route],
    desired_time: time,
    window_minutes: int = 15
) -> List[Route]:
    """
    Keep routes departing within ±window_minutes of desired time.
    Also filters out past departures.
    """
    current_time = datetime.now().time()
    lower_bound = add_minutes(desired_time, -window_minutes)
    upper_bound = add_minutes(desired_time, window_minutes)
    
    return [
        r for r in routes
        if lower_bound <= r.departure_time <= upper_bound
        and r.departure_time >= current_time
    ]
```

---

### 3. Route Matching Logic

**Goal**: Determine if route can fulfill rider's journey

**Match Types**:
1. **Exact Match** (Score: 1.0): Origin and destination both on route
2. **Partial Match** (Score: 0.7): Only origin or destination on route
3. **No Match** (Score: 0.0): Neither origin nor destination on route

**Directionality Check**:
```python
def check_directionality(
    route_stops: List[RouteStop],
    origin_stop_id: UUID,
    dest_stop_id: UUID
) -> bool:
    """
    Ensure destination appears AFTER origin in stop sequence.
    """
    origin_order = None
    dest_order = None
    
    for rs in route_stops:
        if rs.stop_id == origin_stop_id:
            origin_order = rs.stop_order
        if rs.stop_id == dest_stop_id:
            dest_order = rs.stop_order
    
    if origin_order is not None and dest_order is not None:
        return dest_order > origin_order
    
    return False
```

---

### 4. Composite Scoring Algorithm

**Formula**:
```
final_score = (
    w1 * route_match_score +
    w2 * time_match_score +
    w3 * rating_score +
    w4 * price_score
)

where: w1 + w2 + w3 + w4 = 1.0
```

**Default Weights**:
- Route Match: 40% (w1 = 0.4)
- Time Match: 30% (w2 = 0.3)
- Driver Rating: 20% (w3 = 0.2)
- Price: 10% (w4 = 0.1)

**Component Calculations**:

```python
# 1. Route Match Score
def calculate_route_match_score(
    has_origin: bool,
    has_destination: bool,
    correct_direction: bool
) -> float:
    if has_origin and has_destination and correct_direction:
        return 1.0  # Exact match
    elif has_origin or has_destination:
        return 0.7  # Partial match
    else:
        return 0.0  # No match

# 2. Time Match Score
def calculate_time_match_score(
    route_time: time,
    desired_time: time,
    max_window_minutes: int = 15
) -> float:
    diff_minutes = abs(time_diff_minutes(route_time, desired_time))
    if diff_minutes <= max_window_minutes:
        return 1.0 - (diff_minutes / max_window_minutes)
    return 0.0

# 3. Rating Score
def calculate_rating_score(driver_rating: float) -> float:
    # Normalize 0-5 rating to 0-1
    return driver_rating / 5.0

# 4. Price Score (inverse - lower is better)
def calculate_price_score(
    price: float,
    max_price: float,
    min_price: float
) -> float:
    if max_price == min_price:
        return 1.0
    # Inverse normalization
    return 1.0 - ((price - min_price) / (max_price - min_price))
```

---

### 5. Explanation Generation

**Template System**:

```python
def generate_explanation(match_result: MatchResult) -> str:
    """
    Generate human-readable explanation for match score.
    """
    explanations = []
    
    # Route match explanation
    if match_result.route_match_score == 1.0:
        explanations.append(
            "✓ Exact match: Route covers both your origin and destination"
        )
    elif match_result.route_match_score == 0.7:
        explanations.append(
            "~ Partial match: Route covers your origin or destination"
        )
    
    # Time match explanation
    if match_result.time_match_score >= 0.8:
        time_diff = match_result.time_difference_minutes
        explanations.append(
            f"✓ Great timing: Departs {time_diff} minutes from your preferred time"
        )
    
    # Rating explanation
    if match_result.driver_rating >= 4.5:
        explanations.append(
            f"✓ Highly rated driver: {match_result.driver_rating:.1f}/5.0 stars"
        )
    
    # Price explanation
    if match_result.price_score >= 0.7:
        explanations.append(
            f"✓ Good price: ₦{match_result.price:.2f}"
        )
    
    return " | ".join(explanations)
```

---

## API Endpoints Design

### Search & Discovery Service

#### 1. Search Routes
```
GET /v1/routes?lat={lat}&lng={lng}&destLat={destLat}&destLng={destLng}&time={iso8601}&radius={km}
```

**Request Parameters**:
- `lat`, `lng`: Origin coordinates (required)
- `destLat`, `destLng`: Destination coordinates (optional)
- `time`: Desired departure time ISO8601 (optional, defaults to now)
- `radius`: Search radius in km (optional, default: 5.0)
- `limit`: Max results (optional, default: 20)

**Response**:
```json
{
  "results": [
    {
      "id": "uuid",
      "name": "Lekki to VI Express",
      "driver": {
        "id": "uuid",
        "name": "John Doe",
        "rating": 4.8
      },
      "departure_time": "07:00:00",
      "seats_available": 3,
      "price": 1500.00,
      "stops": [...],
      "match_score": 0.92,
      "explanation": "✓ Exact match..."
    }
  ],
  "total": 15,
  "page": 1,
  "limit": 20
}
```

#### 2. Get Route Details
```
GET /v1/routes/{routeId}
```

**Response**: Full route details with all stops

---

### Matchmaking Service (Internal)

#### 1. Match Riders to Routes
```
POST /v1/match
```

**Request**:
```json
{
  "rider_id": "uuid",
  "origin_lat": 6.4302,
  "origin_lon": 3.5066,
  "dest_lat": 6.4281,
  "dest_lon": 3.4219,
  "desired_time": "07:00:00",
  "max_price": 2000.00,
  "min_seats": 1
}
```

**Response**:
```json
{
  "matches": [
    {
      "route_id": "uuid",
      "driver_id": "uuid",
      "final_score": 0.92,
      "scores": {
        "route_match": 1.0,
        "time_match": 0.95,
        "rating": 0.96,
        "price": 0.80
      },
      "explanation": "...",
      "recommended": true
    }
  ],
  "total_candidates": 45,
  "matched_candidates": 12,
  "execution_time_ms": 87
}
```

---

## Performance Optimization Strategy

### Target: P95 Latency ≤ 200ms

**Strategies**:

1. **Database Optimization**
   - Spatial indexes (GIST) on all geometry columns
   - Composite indexes on (status, departure_time)
   - Connection pooling (min: 10, max: 50)

2. **Caching Strategy**
   - Redis cache for hot routes (TTL: 5 min)
   - Cache key pattern: `routes:search:{lat}:{lng}:{time}`
   - Cache active routes list (TTL: 1 min)

3. **Query Optimization**
   - Use geography type for accurate distance
   - Limit candidate set to 50 routes max
   - Eager load route_stops with selectinload

4. **Async Processing**
   - All database queries async
   - Parallel score calculation where possible
   - Non-blocking Redis operations

5. **Response Optimization**
   - Pagination (default: 20 results)
   - Partial field selection
   - Compression for large responses

---

## Redis Caching Patterns

### Hot Route Cache
```python
# Cache structure
{
  "routes:active": [route_id1, route_id2, ...],  # TTL: 60s
  "route:details:{id}": {...},  # TTL: 300s
  "search:results:{hash}": {...},  # TTL: 180s
}
```

### Cache Invalidation
- On route update/delete: Invalidate route details
- On new route: Invalidate active routes list
- On booking: Invalidate if seats_available changes

---

## Implementation Checklist

### Matchmaking Service
- [ ] Project structure and config
- [ ] Database models and migrations
- [ ] Geospatial utilities
- [ ] Scoring algorithms
- [ ] Caching layer
- [ ] API endpoints
- [ ] Tests (unit + integration)
- [ ] Performance testing

### Search Service
- [ ] Project structure and config
- [ ] Search endpoints
- [ ] Caching integration
- [ ] Tests
- [ ] Documentation

---

## Testing Strategy

### Unit Tests
- Geospatial calculations (bearing, distance)
- Scoring algorithms (all components)
- Time matching logic
- Route matching logic
- Explanation generation

### Integration Tests
- Search API with real database
- Match API with multiple scenarios
- Cache hit/miss scenarios
- Performance benchmarks

### Performance Tests
- Load test: 100 req/s
- Latency test: P95 < 200ms
- Cache effectiveness
- Database query optimization

---

## Next Steps

1. ✅ Create this implementation plan
2. 🚧 Set up Matchmaking Service structure
3. ⏳ Set up Search Service structure
4. ⏳ Implement database migrations
5. ⏳ Build geospatial algorithms
6. ⏳ Implement scoring system
7. ⏳ Add Redis caching
8. ⏳ Create API endpoints
9. ⏳ Write comprehensive tests
10. ⏳ Performance optimization
11. ⏳ Documentation

---

**Status**: Ready to begin implementation 🚀

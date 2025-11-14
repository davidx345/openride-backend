# OpenRide Data Architecture Alignment Plan

**Document Version**: 1.0  
**Created**: November 14, 2025  
**Status**: Planning Phase  
**Correlation Score**: 65% → Target: 100%

---

## Executive Summary

This document outlines the implementation plan to align the OpenRide backend with the data architecture specified in `PRD/data.txt`. The current implementation uses **stop-based routing** while the specification requires **hub-based routing** with comprehensive analytics and ML features.

### Current State
- ✅ Stop-based geospatial routing (PostGIS)
- ✅ Booking service with state machine
- ✅ Analytics service with ClickHouse + Kafka
- ❌ No hub infrastructure
- ❌ No search event logging
- ❌ No in-memory caching
- ❌ No ML feature engineering

### Target State
- Hub-centric routing with stop associations
- Complete search and booking event logging
- Redis-based route and driver stats caching
- ML-ready feature extraction pipeline
- Historical metrics aggregation

---

## Phase 1: Core Data Model Enhancement (Week 1-2)

### 1.1 Create Hubs Infrastructure

**Objective**: Introduce hub entities as routing origins and demand/supply aggregation points.

#### Database Migration: `V12_001__create_hubs_table.sql`

```sql
-- Create hubs table
CREATE TABLE hubs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    lat NUMERIC(10, 8) NOT NULL,
    lon NUMERIC(11, 8) NOT NULL,
    location GEOMETRY(POINT, 4326) NOT NULL,
    area_id VARCHAR(50),  -- e.g., 'VI', 'Lekki', 'Mainland'
    zone VARCHAR(100),    -- e.g., 'Island', 'Mainland'
    is_active BOOLEAN DEFAULT true NOT NULL,
    address VARCHAR(500),
    landmark VARCHAR(200),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT uq_hub_coordinates UNIQUE (lat, lon)
);

-- Spatial index for hub location
CREATE INDEX idx_hubs_location ON hubs USING GIST (location);

-- Indexes for filtering
CREATE INDEX idx_hubs_area ON hubs(area_id);
CREATE INDEX idx_hubs_zone ON hubs(zone);
CREATE INDEX idx_hubs_active ON hubs(is_active);

-- Trigger to auto-update location from lat/lon
CREATE OR REPLACE FUNCTION update_hub_location()
RETURNS TRIGGER AS $$
BEGIN
    NEW.location = ST_SetSRID(ST_MakePoint(NEW.lon, NEW.lat), 4326);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_hubs_location
    BEFORE INSERT OR UPDATE OF lat, lon ON hubs
    FOR EACH ROW
    EXECUTE FUNCTION update_hub_location();

COMMENT ON TABLE hubs IS 'Transportation hubs for route origin and demand aggregation';
COMMENT ON COLUMN hubs.area_id IS 'Area/region identifier (e.g., Victoria Island, Lekki)';
COMMENT ON COLUMN hubs.zone IS 'Zone grouping (e.g., Island, Mainland)';
```

#### Python Model: `driver-service/app/models/hub.py`

```python
from uuid import uuid4
from sqlalchemy import Column, String, Boolean, Numeric, Index
from sqlalchemy.dialects.postgresql import UUID
from geoalchemy2 import Geometry
from app.core.database import Base

class Hub(Base):
    """Hub model for route origin points."""
    __tablename__ = "hubs"
    
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    name = Column(String(200), nullable=False)
    lat = Column(Numeric(10, 8), nullable=False)
    lon = Column(Numeric(11, 8), nullable=False)
    location = Column(Geometry('POINT', srid=4326), nullable=False)
    area_id = Column(String(50), nullable=True, index=True)
    zone = Column(String(100), nullable=True)
    is_active = Column(Boolean, default=True, nullable=False, index=True)
    address = Column(String(500), nullable=True)
    landmark = Column(String(200), nullable=True)
    
    __table_args__ = (
        Index('idx_hubs_location', 'location', postgresql_using='gist'),
    )
```

**Tasks**:
- [ ] Create migration file
- [ ] Add Hub model to driver-service
- [ ] Add Hub model to matchmaking-service
- [ ] Create HubRepository with geospatial queries
- [ ] Seed initial hubs (Lagos: VI, Lekki, Ikeja, etc.)

---

### 1.2 Enhance Stops Table

**Objective**: Link stops to hubs and add regional metadata.

#### Database Migration: `V12_002__enhance_stops_table.sql`

```sql
-- Add new columns to stops
ALTER TABLE stops ADD COLUMN hub_id UUID REFERENCES hubs(id) ON DELETE SET NULL;
ALTER TABLE stops ADD COLUMN area_id VARCHAR(50);
ALTER TABLE stops ADD COLUMN is_active BOOLEAN DEFAULT true NOT NULL;

-- Create indexes
CREATE INDEX idx_stops_hub ON stops(hub_id);
CREATE INDEX idx_stops_area ON stops(area_id);
CREATE INDEX idx_stops_active ON stops(is_active);

-- Update trigger to inherit area from hub
CREATE OR REPLACE FUNCTION inherit_area_from_hub()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.hub_id IS NOT NULL THEN
        SELECT area_id INTO NEW.area_id 
        FROM hubs 
        WHERE id = NEW.hub_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stops_inherit_area
    BEFORE INSERT OR UPDATE OF hub_id ON stops
    FOR EACH ROW
    EXECUTE FUNCTION inherit_area_from_hub();

COMMENT ON COLUMN stops.hub_id IS 'Associated hub (if this stop is a hub stop)';
COMMENT ON COLUMN stops.area_id IS 'Area/region identifier inherited from hub or manually set';
```

**Tasks**:
- [ ] Create migration
- [ ] Update Stop models in driver-service and matchmaking-service
- [ ] Update StopRepository to support hub filtering
- [ ] Data migration script to assign existing stops to nearest hub

---

### 1.3 Add Hub Support to Routes

**Objective**: Routes should reference origin hub for efficient candidate retrieval.

#### Database Migration: `V12_003__add_hub_to_routes.sql`

```sql
-- Add hub reference to routes
ALTER TABLE routes ADD COLUMN origin_hub_id UUID REFERENCES hubs(id);
ALTER TABLE routes ADD COLUMN destination_hub_id UUID REFERENCES hubs(id);
ALTER TABLE routes ADD COLUMN currency VARCHAR(3) DEFAULT 'NGN' NOT NULL;
ALTER TABLE routes ADD COLUMN estimated_duration_minutes INTEGER;
ALTER TABLE routes ADD COLUMN route_template_id UUID;

-- Create indexes
CREATE INDEX idx_routes_origin_hub ON routes(origin_hub_id);
CREATE INDEX idx_routes_dest_hub ON routes(destination_hub_id);
CREATE INDEX idx_routes_template ON routes(route_template_id);
CREATE INDEX idx_routes_hub_status_time ON routes(origin_hub_id, status, departure_time);

-- Function to auto-assign hub from first stop
CREATE OR REPLACE FUNCTION assign_route_hub()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.origin_hub_id IS NULL THEN
        -- Get hub from first route stop
        SELECT s.hub_id INTO NEW.origin_hub_id
        FROM route_stops rs
        JOIN stops s ON rs.stop_id = s.id
        WHERE rs.route_id = NEW.id
        ORDER BY rs.stop_order ASC
        LIMIT 1;
    END IF;
    
    IF NEW.destination_hub_id IS NULL THEN
        -- Get hub from last route stop
        SELECT s.hub_id INTO NEW.destination_hub_id
        FROM route_stops rs
        JOIN stops s ON rs.stop_id = s.id
        WHERE rs.route_id = NEW.id
        ORDER BY rs.stop_order DESC
        LIMIT 1;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_routes_assign_hub
    BEFORE INSERT OR UPDATE ON routes
    FOR EACH ROW
    EXECUTE FUNCTION assign_route_hub();

COMMENT ON COLUMN routes.origin_hub_id IS 'Origin hub for fast candidate retrieval';
COMMENT ON COLUMN routes.route_template_id IS 'Optional link to route template/pattern';
```

**Tasks**:
- [ ] Create migration
- [ ] Update Route models
- [ ] Update RouteRepository.find_nearby_routes() to filter by origin_hub_id
- [ ] Data migration to assign hubs to existing routes

---

### 1.4 Enhance Driver Profiles

**Objective**: Add missing driver metrics for ML features.

#### Database Migration: `V12_004__enhance_driver_profiles.sql`

```sql
-- Add missing columns to driver_profiles
ALTER TABLE driver_profiles ADD COLUMN rating_avg NUMERIC(3, 2) DEFAULT 0.00;
ALTER TABLE driver_profiles ADD COLUMN rating_count INTEGER DEFAULT 0;
ALTER TABLE driver_profiles ADD COLUMN cancellation_rate NUMERIC(5, 4) DEFAULT 0.0000;
ALTER TABLE driver_profiles ADD COLUMN completed_trips INTEGER DEFAULT 0;
ALTER TABLE driver_profiles ADD COLUMN cancelled_trips INTEGER DEFAULT 0;
ALTER TABLE driver_profiles ADD COLUMN is_verified BOOLEAN DEFAULT false;

-- Add constraints
ALTER TABLE driver_profiles ADD CONSTRAINT chk_rating_avg CHECK (rating_avg >= 0 AND rating_avg <= 5);
ALTER TABLE driver_profiles ADD CONSTRAINT chk_rating_count CHECK (rating_count >= 0);
ALTER TABLE driver_profiles ADD CONSTRAINT chk_cancellation_rate CHECK (cancellation_rate >= 0 AND cancellation_rate <= 1);

-- Create indexes
CREATE INDEX idx_driver_profiles_rating ON driver_profiles(rating_avg);
CREATE INDEX idx_driver_profiles_verified ON driver_profiles(is_verified);

COMMENT ON COLUMN driver_profiles.rating_avg IS 'Average rating (0.00 to 5.00)';
COMMENT ON COLUMN driver_profiles.cancellation_rate IS 'Cancellation rate (0.0000 to 1.0000)';
```

**Tasks**:
- [ ] Create migration
- [ ] Update DriverProfile model in user-service
- [ ] Create background job to compute rating_avg from user ratings
- [ ] Create background job to compute cancellation_rate from bookings

---

### 1.5 Add Rider Metrics

**Objective**: Track rider behavior for fraud detection and personalization.

#### Database Migration: `V12_005__add_rider_metrics.sql`

```sql
-- Create rider_profiles table
CREATE TABLE rider_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    completed_trips INTEGER DEFAULT 0,
    cancelled_trips INTEGER DEFAULT 0,
    no_show_count INTEGER DEFAULT 0,
    total_spent NUMERIC(12, 2) DEFAULT 0.00,
    average_rating NUMERIC(3, 2) DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    CONSTRAINT chk_completed_trips CHECK (completed_trips >= 0),
    CONSTRAINT chk_cancelled_trips CHECK (cancelled_trips >= 0),
    CONSTRAINT chk_no_show_count CHECK (no_show_count >= 0),
    CONSTRAINT chk_total_spent CHECK (total_spent >= 0)
);

CREATE INDEX idx_rider_profiles_user ON rider_profiles(user_id);

COMMENT ON TABLE rider_profiles IS 'Rider behavior metrics for ML and fraud detection';
```

**Tasks**:
- [ ] Create migration
- [ ] Create RiderProfile model in user-service
- [ ] Create triggers to update metrics on booking status changes
- [ ] Backfill metrics from existing bookings

---

### 1.6 Link Bookings to Search Events

**Objective**: Enable conversion tracking and ML training labels.

#### Database Migration: `V12_006__add_search_id_to_bookings.sql`

```sql
-- Add search tracking to bookings
ALTER TABLE bookings ADD COLUMN search_id UUID;
ALTER TABLE bookings ADD COLUMN candidate_rank INTEGER;
ALTER TABLE bookings ADD COLUMN candidate_count INTEGER;

CREATE INDEX idx_bookings_search ON bookings(search_id);

COMMENT ON COLUMN bookings.search_id IS 'Link to search event that produced this booking';
COMMENT ON COLUMN bookings.candidate_rank IS 'Rank of chosen route in search results (1=top)';
COMMENT ON COLUMN bookings.candidate_count IS 'Total candidates in search results';
```

**Tasks**:
- [ ] Create migration
- [ ] Update Booking model
- [ ] Update BookingService to accept search_id
- [ ] Update matchmaking-service to return search_id with results

---

## Phase 2: Runtime Algorithm Data (Week 3-4)

### 2.1 Implement Hub/Stop Spatial Index Service

**Objective**: Fast nearest-hub/stop resolution.

#### Create: `matchmaking-service/app/services/spatial_index_service.py`

```python
"""Spatial index service for hub and stop resolution."""
from typing import Tuple, Optional
from uuid import UUID
from sklearn.neighbors import BallTree
import numpy as np
from sqlalchemy.ext.asyncio import AsyncSession

class SpatialIndexService:
    """In-memory spatial index using BallTree for fast nearest-neighbor queries."""
    
    def __init__(self):
        self.hub_tree: Optional[BallTree] = None
        self.hub_ids: list[UUID] = []
        self.hub_coords: np.ndarray = None
        
        self.stop_tree: Optional[BallTree] = None
        self.stop_ids: list[UUID] = []
        self.stop_coords: np.ndarray = None
    
    async def build_hub_index(self, db: AsyncSession):
        """Build BallTree index from active hubs."""
        from app.models.hub import Hub
        from sqlalchemy import select
        
        stmt = select(Hub).where(Hub.is_active == True)
        result = await db.execute(stmt)
        hubs = result.scalars().all()
        
        self.hub_ids = [h.id for h in hubs]
        self.hub_coords = np.array([[float(h.lat), float(h.lon)] for h in hubs])
        self.hub_tree = BallTree(np.radians(self.hub_coords), metric='haversine')
    
    async def build_stop_index(self, db: AsyncSession):
        """Build BallTree index from active stops."""
        from app.models.stop import Stop
        from sqlalchemy import select
        
        stmt = select(Stop).where(Stop.is_active == True)
        result = await db.execute(stmt)
        stops = result.scalars().all()
        
        self.stop_ids = [s.id for s in stops]
        self.stop_coords = np.array([[float(s.lat), float(s.lon)] for s in stops])
        self.stop_tree = BallTree(np.radians(self.stop_coords), metric='haversine')
    
    def find_nearest_hub(self, lat: float, lon: float, radius_km: float = 1.0) -> Optional[UUID]:
        """Find nearest hub within radius."""
        if self.hub_tree is None:
            return None
        
        query_point = np.radians([[lat, lon]])
        radius_rad = radius_km / 6371.0  # Earth radius in km
        
        indices, distances = self.hub_tree.query_radius(query_point, r=radius_rad, return_distance=True)
        
        if len(indices[0]) > 0:
            nearest_idx = indices[0][np.argmin(distances[0])]
            return self.hub_ids[nearest_idx]
        
        return None
    
    def find_nearest_stop(self, lat: float, lon: float, radius_km: float = 0.15) -> Optional[UUID]:
        """Find nearest stop within radius (default 150m)."""
        if self.stop_tree is None:
            return None
        
        query_point = np.radians([[lat, lon]])
        radius_rad = radius_km / 6371.0
        
        indices, distances = self.stop_tree.query_radius(query_point, r=radius_rad, return_distance=True)
        
        if len(indices[0]) > 0:
            nearest_idx = indices[0][np.argmin(distances[0])]
            return self.stop_ids[nearest_idx]
        
        return None

# Global instance
spatial_index = SpatialIndexService()
```

**Tasks**:
- [ ] Create SpatialIndexService
- [ ] Add scikit-learn to requirements.txt
- [ ] Create background job to rebuild index every 5 minutes
- [ ] Integrate into matching_service.py

---

### 2.2 Implement Redis Route Cache

**Objective**: Avoid database hits on every search request.

#### Create: `matchmaking-service/app/services/route_cache_service.py`

```python
"""Redis-based route cache for fast candidate retrieval."""
from typing import Optional, Sequence
from uuid import UUID
from datetime import time
import json
import redis.asyncio as redis
from app.models.route import Route

class RouteCacheService:
    """Cache active routes in Redis for fast retrieval."""
    
    def __init__(self, redis_client: redis.Redis):
        self.redis = redis_client
        self.cache_ttl = 300  # 5 minutes
    
    async def cache_active_routes(self, routes: Sequence[Route]):
        """Cache active routes by origin_hub_id."""
        pipeline = self.redis.pipeline()
        
        for route in routes:
            if route.status != "ACTIVE" or route.seats_available <= 0:
                continue
            
            route_data = {
                "id": str(route.id),
                "driver_id": str(route.driver_id),
                "origin_hub_id": str(route.origin_hub_id) if route.origin_hub_id else None,
                "departure_time": route.departure_time.isoformat(),
                "seats_available": route.seats_available,
                "base_price": float(route.base_price),
                "status": route.status,
            }
            
            # Cache by hub
            if route.origin_hub_id:
                key = f"routes:hub:{route.origin_hub_id}"
                pipeline.rpush(key, json.dumps(route_data))
                pipeline.expire(key, self.cache_ttl)
        
        await pipeline.execute()
    
    async def get_routes_by_hub(self, hub_id: UUID) -> list[dict]:
        """Get cached routes for a hub."""
        key = f"routes:hub:{hub_id}"
        routes_json = await self.redis.lrange(key, 0, -1)
        return [json.loads(r) for r in routes_json]
    
    async def invalidate_route(self, route_id: UUID):
        """Invalidate cached route (on booking/cancellation)."""
        # Scan and delete from all hub lists
        cursor = 0
        while True:
            cursor, keys = await self.redis.scan(cursor, match="routes:hub:*")
            for key in keys:
                await self.redis.delete(key)
            if cursor == 0:
                break
```

**Tasks**:
- [ ] Create RouteCacheService
- [ ] Integrate into matchmaking-service startup
- [ ] Add cache refresh background job (every 5 min)
- [ ] Update matching logic to check cache first

---

### 2.3 Implement Driver Stats Cache

**Objective**: Avoid user-service calls on every match.

#### Database Migration: `V12_007__create_driver_stats_cache.sql`

```sql
CREATE TABLE driver_stats_cache (
    driver_id UUID PRIMARY KEY,
    rating_avg NUMERIC(3, 2),
    rating_count INTEGER,
    cancellation_rate NUMERIC(5, 4),
    completed_trips INTEGER,
    last_updated TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    INDEX idx_driver_stats_updated (last_updated)
);

COMMENT ON TABLE driver_stats_cache IS 'Precomputed driver statistics for fast ML feature lookup';
```

#### Create: `matchmaking-service/app/repositories/driver_stats_repository.py`

```python
"""Driver stats cache repository."""
from uuid import UUID
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession
from app.models.driver_stats import DriverStatsCache

class DriverStatsRepository:
    """Repository for cached driver statistics."""
    
    def __init__(self, db: AsyncSession):
        self.db = db
    
    async def get_driver_stats(self, driver_id: UUID) -> dict:
        """Get cached driver stats."""
        stmt = select(DriverStatsCache).where(DriverStatsCache.driver_id == driver_id)
        result = await self.db.execute(stmt)
        stats = result.scalar_one_or_none()
        
        if stats:
            return {
                "rating_avg": float(stats.rating_avg) if stats.rating_avg else 0.0,
                "rating_count": stats.rating_count or 0,
                "cancellation_rate": float(stats.cancellation_rate) if stats.cancellation_rate else 0.0,
                "completed_trips": stats.completed_trips or 0,
            }
        
        return None
    
    async def refresh_from_user_service(self, driver_id: UUID, stats: dict):
        """Update cache from user service."""
        # Upsert logic here
        pass
```

**Tasks**:
- [ ] Create migration
- [ ] Create DriverStatsCache model
- [ ] Create DriverStatsRepository
- [ ] Create background job to refresh from user-service every hour
- [ ] Update matching_service to use cache

---

## Phase 3: Event Logging & Analytics (Week 5-6)

### 3.1 Implement Search Event Logging

**Objective**: Log every search for ML training and metrics.

#### ClickHouse Schema: `analytics-service/clickhouse/002_search_events.sql`

```sql
CREATE TABLE IF NOT EXISTS openride_analytics.search_events (
    search_id UUID,
    timestamp DateTime64(3),
    rider_id UUID,
    origin_hub_id Nullable(UUID),
    pickup_stop_id Nullable(UUID),
    destination_stop_id Nullable(UUID),
    desired_departure_time Time,
    time_window_minutes Int32,
    seats_requested Int32,
    max_price Nullable(Decimal(10, 2)),
    radius_km Float32,
    total_candidates Int32,
    matched_candidates Int32,
    execution_time_ms Int32,
    date Date MATERIALIZED toDate(timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (timestamp, search_id);

CREATE TABLE IF NOT EXISTS openride_analytics.search_candidates (
    search_id UUID,
    route_id UUID,
    driver_id UUID,
    rank_position Int32,
    match_type String,  -- 'EXACT', 'PARTIAL'
    route_match_score Float32,
    time_match_score Float32,
    rating_score Float32,
    price_score Float32,
    final_score Float32,
    timestamp DateTime64(3),
    date Date MATERIALIZED toDate(timestamp)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (search_id, rank_position);
```

#### Update: `matchmaking-service/app/services/matching_service.py`

```python
async def match_routes(self, request: MatchRequest) -> MatchResponse:
    """Match routes and log search event."""
    import uuid
    
    search_id = uuid.uuid4()
    start_time = time.time()
    
    # ... existing matching logic ...
    
    # Log search event
    await self._log_search_event(
        search_id=search_id,
        request=request,
        total_candidates=total_candidates,
        matched_candidates=matched_candidates,
        execution_time_ms=execution_time,
    )
    
    # Log candidates
    await self._log_search_candidates(search_id, match_results)
    
    # Add search_id to response
    return MatchResponse(
        search_id=search_id,  # NEW
        matches=top_matches,
        total_candidates=total_candidates,
        matched_candidates=matched_candidates,
        execution_time_ms=execution_time,
    )

async def _log_search_event(self, search_id, request, total_candidates, matched_candidates, execution_time_ms):
    """Send search event to analytics via Kafka."""
    from app.core.kafka import kafka_producer
    
    event = {
        "search_id": str(search_id),
        "timestamp": datetime.utcnow().isoformat(),
        "rider_id": str(request.rider_id),
        "origin_lat": request.origin_lat,
        "origin_lon": request.origin_lon,
        "dest_lat": request.dest_lat,
        "dest_lon": request.dest_lon,
        "desired_time": request.desired_time.isoformat(),
        "total_candidates": total_candidates,
        "matched_candidates": matched_candidates,
        "execution_time_ms": execution_time_ms,
    }
    
    await kafka_producer.send("openride.events.search", value=event)
```

**Tasks**:
- [ ] Create ClickHouse search events schema
- [ ] Add Kafka producer to matchmaking-service
- [ ] Update matching_service to log searches
- [ ] Create analytics-service consumer for search events
- [ ] Create dashboard queries for search metrics

---

### 3.2 Link Bookings to Search Events

**Objective**: Track conversion funnel and ML training labels.

#### Update: `booking-service/src/main/java/com/openride/booking/service/BookingService.java`

```java
public Booking createBooking(BookingCreateRequest request) {
    // ... existing logic ...
    
    booking.setSearchId(request.getSearchId());  // NEW
    booking.setCandidateRank(request.getCandidateRank());  // NEW
    booking.setCandidateCount(request.getCandidateCount());  // NEW
    
    // ... save booking ...
    
    // Publish event with search context
    BookingCreatedEvent event = new BookingCreatedEvent();
    event.setSearchId(booking.getSearchId());
    eventPublisher.publish(event);
    
    return booking;
}
```

**Tasks**:
- [ ] Update BookingCreateRequest DTO
- [ ] Update BookingService
- [ ] Update frontend to pass search_id from match results
- [ ] Create analytics query: Top-1, Top-3 hit rates

---

### 3.3 Implement Aggregated Stats Tables

**Objective**: Precompute metrics for fast dashboard queries.

#### Database Migration: `V12_008__create_aggregated_stats.sql`

```sql
-- Hub demand/supply metrics
CREATE TABLE hub_metrics (
    hub_id UUID,
    date DATE,
    hour INTEGER,  -- 0-23
    search_count INTEGER DEFAULT 0,
    active_routes INTEGER DEFAULT 0,
    bookings_count INTEGER DEFAULT 0,
    demand_supply_ratio NUMERIC(10, 4),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    PRIMARY KEY (hub_id, date, hour)
);

CREATE INDEX idx_hub_metrics_date ON hub_metrics(date);

-- Driver performance metrics
CREATE TABLE driver_daily_metrics (
    driver_id UUID,
    date DATE,
    trips_completed INTEGER DEFAULT 0,
    trips_cancelled INTEGER DEFAULT 0,
    revenue NUMERIC(12, 2) DEFAULT 0.00,
    ratings_received INTEGER DEFAULT 0,
    average_rating NUMERIC(3, 2),
    online_hours NUMERIC(5, 2),
    
    PRIMARY KEY (driver_id, date)
);

-- Route popularity metrics
CREATE TABLE route_metrics (
    route_id UUID,
    date DATE,
    searches_matched INTEGER DEFAULT 0,
    bookings_count INTEGER DEFAULT 0,
    conversion_rate NUMERIC(5, 4),
    average_score NUMERIC(3, 2),
    
    PRIMARY KEY (route_id, date)
);
```

#### Create Background Jobs: `analytics-service/app/jobs/aggregate_metrics.py`

```python
"""Background jobs to aggregate metrics."""
from datetime import datetime, timedelta

async def aggregate_hub_metrics():
    """Run hourly - aggregate hub search/supply metrics."""
    # Query search_events and routes
    # Insert into hub_metrics
    pass

async def aggregate_driver_metrics():
    """Run daily - aggregate driver performance."""
    # Query bookings and ratings
    # Insert into driver_daily_metrics
    pass

async def aggregate_route_metrics():
    """Run daily - aggregate route popularity."""
    # Query search_candidates and bookings
    # Compute conversion rates
    pass
```

**Tasks**:
- [ ] Create aggregated stats tables
- [ ] Create aggregation jobs (Celery/APScheduler)
- [ ] Schedule jobs: hourly for hubs, daily for drivers/routes
- [ ] Create dashboard endpoints using aggregated tables

---

## Phase 4: ML Feature Engineering (Week 7-8)

### 4.1 Feature Vector Extraction Service

**Objective**: Extract ML-ready features for ranking model.

#### Create: `matchmaking-service/app/services/feature_extraction_service.py`

```python
"""ML feature extraction for route ranking."""
import numpy as np
from datetime import time, datetime
from typing import Sequence
from app.models.route import Route
from app.schemas.matching import MatchRequest

class FeatureExtractionService:
    """Extract feature vectors for ML ranking model."""
    
    FEATURES = [
        # Categorical (encoded)
        'origin_hub_id_encoded',
        'dest_stop_id_encoded',
        'dest_area_id_encoded',
        
        # Temporal
        'hour_of_day',
        'day_of_week',
        'is_weekend',
        'is_rush_hour',
        
        # Match quality
        'match_type_exact',  # 1 if EXACT, 0 if PARTIAL
        'time_diff_minutes',
        'time_diff_normalized',
        
        # Pricing
        'price_per_seat',
        'price_rank',  # Rank within candidates
        'price_percentile',
        
        # Route characteristics
        'route_length',  # Number of stops
        'destination_position',  # Index of dest in route
        'destination_position_normalized',
        
        # Driver features
        'driver_rating',
        'driver_rating_count',
        'driver_cancellation_rate',
        'driver_completed_trips',
        
        # Availability
        'seats_available',
        'seats_utilization',  # seats_available / seats_total
    ]
    
    def extract_features(
        self, 
        route: Route, 
        request: MatchRequest,
        all_prices: list,
        driver_stats: dict,
        match_type: str
    ) -> np.ndarray:
        """Extract feature vector for a single route."""
        
        features = []
        
        # Temporal features
        desired_dt = datetime.combine(datetime.today(), request.desired_time)
        features.extend([
            desired_dt.hour,
            desired_dt.weekday(),
            1 if desired_dt.weekday() >= 5 else 0,
            1 if 7 <= desired_dt.hour <= 9 or 17 <= desired_dt.hour <= 19 else 0,
        ])
        
        # Match type
        features.append(1 if match_type == 'EXACT' else 0)
        
        # Time difference
        route_minutes = route.departure_time.hour * 60 + route.departure_time.minute
        desired_minutes = request.desired_time.hour * 60 + request.desired_time.minute
        time_diff = abs(route_minutes - desired_minutes)
        features.extend([
            time_diff,
            time_diff / 60.0,  # Normalized
        ])
        
        # Pricing
        price = float(route.base_price)
        features.extend([
            price,
            sorted(all_prices).index(price) + 1,  # Rank
            (price - min(all_prices)) / (max(all_prices) - min(all_prices)) if len(all_prices) > 1 else 0.5,
        ])
        
        # Route characteristics
        route_length = len(route.route_stops) if route.route_stops else 0
        features.extend([
            route_length,
            route_length // 2,  # Simplified destination position
            0.5,  # Normalized position
        ])
        
        # Driver features
        features.extend([
            driver_stats.get('rating_avg', 0.0),
            driver_stats.get('rating_count', 0),
            driver_stats.get('cancellation_rate', 0.0),
            driver_stats.get('completed_trips', 0),
        ])
        
        # Availability
        features.extend([
            route.seats_available,
            route.seats_available / route.seats_total,
        ])
        
        return np.array(features, dtype=np.float32)
    
    def extract_batch_features(
        self,
        routes: Sequence[Route],
        request: MatchRequest,
        driver_stats_map: dict,
        match_types: dict
    ) -> np.ndarray:
        """Extract features for all candidates."""
        
        all_prices = [float(r.base_price) for r in routes]
        
        feature_matrix = []
        for route in routes:
            driver_stats = driver_stats_map.get(str(route.driver_id), {})
            match_type = match_types.get(str(route.id), 'PARTIAL')
            
            features = self.extract_features(
                route, request, all_prices, driver_stats, match_type
            )
            feature_matrix.append(features)
        
        return np.array(feature_matrix)
```

**Tasks**:
- [ ] Create FeatureExtractionService
- [ ] Add feature extraction to matching pipeline
- [ ] Log features with search_candidates events
- [ ] Create feature engineering notebook for analysis

---

### 4.2 ML Training Pipeline (Future)

**Objective**: Learn-to-rank model training infrastructure.

#### Create: `ml-pipeline/train_ranking_model.py`

```python
"""Learning-to-rank model training."""
import lightgbm as lgb
from clickhouse_connect import get_client

def fetch_training_data():
    """Fetch labeled training data from ClickHouse."""
    ch = get_client(...)
    
    # Get search candidates with labels (1 if booked, 0 otherwise)
    query = """
    SELECT 
        sc.*,
        CASE WHEN b.id IS NOT NULL THEN 1 ELSE 0 END as label
    FROM openride_analytics.search_candidates sc
    LEFT JOIN openride_analytics.booking_events b 
        ON sc.search_id = b.search_id AND sc.route_id = b.route_id
    WHERE sc.timestamp >= now() - INTERVAL 30 DAY
    """
    
    return ch.query(query).result_rows

def train_model():
    """Train LightGBM ranking model."""
    data = fetch_training_data()
    # ... prepare features and labels ...
    
    train_data = lgb.Dataset(X_train, label=y_train, group=query_groups)
    
    params = {
        'objective': 'lambdarank',
        'metric': 'ndcg',
        'ndcg_eval_at': [1, 3, 5],
    }
    
    model = lgb.train(params, train_data, num_boost_round=100)
    model.save_model('ranking_model.txt')
```

**Tasks**:
- [ ] Create ML pipeline directory
- [ ] Set up training environment
- [ ] Implement data fetching from ClickHouse
- [ ] Train initial LightGBM model
- [ ] Deploy model to matchmaking-service
- [ ] A/B test ML vs rule-based ranking

---

## Phase 5: Testing & Validation (Week 9)

### 5.1 Data Integrity Tests

```python
# Test hub-stop relationships
def test_all_stops_have_hub():
    assert all_stops.hub_id.notnull().all()

# Test route hub assignments
def test_all_routes_have_origin_hub():
    assert active_routes.origin_hub_id.notnull().all()

# Test search-booking linkage
def test_bookings_have_search_id():
    recent_bookings = get_bookings_last_7_days()
    assert recent_bookings.search_id.notnull().sum() > 0
```

**Tasks**:
- [ ] Write data integrity tests
- [ ] Create data validation CI job
- [ ] Set up monitoring alerts for data quality

---

### 5.2 Performance Benchmarks

```python
# Benchmark spatial index
def benchmark_hub_resolution():
    # Should resolve hub in <1ms
    for _ in range(1000):
        hub = spatial_index.find_nearest_hub(6.5244, 3.3792)
    # Assert avg time < 1ms

# Benchmark route cache
def benchmark_route_retrieval():
    # Should retrieve from cache in <5ms
    routes = route_cache.get_routes_by_hub(hub_id)
    # Assert time < 5ms
```

**Tasks**:
- [ ] Create performance test suite
- [ ] Benchmark hub/stop resolution
- [ ] Benchmark route cache retrieval
- [ ] Benchmark feature extraction
- [ ] Compare before/after performance

---

### 5.3 Analytics Validation

```python
# Validate search event logging
def test_search_events_logged():
    count_before = get_search_events_count()
    perform_search()
    count_after = get_search_events_count()
    assert count_after == count_before + 1

# Validate conversion tracking
def test_conversion_funnel():
    metrics = get_conversion_metrics()
    assert metrics['top_1_hit_rate'] > 0
    assert metrics['top_3_hit_rate'] > 0
```

**Tasks**:
- [ ] Validate event logging end-to-end
- [ ] Validate aggregation jobs
- [ ] Create analytics dashboard
- [ ] Set up conversion tracking reports

---

## Implementation Priority

### 🔴 **Critical (Must Have)**
1. Phase 1.1 - Create Hubs Infrastructure
2. Phase 1.3 - Add Hub Support to Routes
3. Phase 1.6 - Link Bookings to Search Events
4. Phase 3.1 - Implement Search Event Logging

### 🟡 **High (Should Have)**
5. Phase 1.2 - Enhance Stops Table
6. Phase 1.4 - Enhance Driver Profiles
7. Phase 2.2 - Implement Redis Route Cache
8. Phase 3.3 - Implement Aggregated Stats Tables

### 🟢 **Medium (Nice to Have)**
9. Phase 1.5 - Add Rider Metrics
10. Phase 2.1 - Implement Spatial Index Service
11. Phase 2.3 - Implement Driver Stats Cache
12. Phase 4.1 - Feature Vector Extraction

### ⚪ **Low (Future)**
13. Phase 4.2 - ML Training Pipeline
14. Advanced analytics and reporting

---

## Success Metrics

### Data Model Alignment
- [ ] All routes have origin_hub_id (100%)
- [ ] All stops have hub_id (100%)
- [ ] Bookings have search_id (>90% for new bookings)
- [ ] Driver profiles have rating metrics (100%)

### Performance
- [ ] Hub resolution: <1ms (p99)
- [ ] Route cache hit rate: >80%
- [ ] Search event logging: <10ms overhead
- [ ] Feature extraction: <50ms per search

### Analytics
- [ ] Search events logged: 100% coverage
- [ ] Conversion tracking: Top-1, Top-3 hit rates measured
- [ ] Hub metrics updated hourly
- [ ] Driver metrics updated daily

### ML Readiness
- [ ] 30 days of search + booking data
- [ ] Feature vectors logged with events
- [ ] Training pipeline functional
- [ ] Initial model trained (NDCG@3 > 0.7)

---

## Migration Strategy

### Backward Compatibility
- All new columns nullable initially
- Gradual backfill of hub assignments
- Dual-write to old and new schemas
- Feature flags for new features

### Rollout Plan
1. **Week 1-2**: Deploy database migrations
2. **Week 3-4**: Backfill existing data
3. **Week 5-6**: Enable event logging
4. **Week 7-8**: Enable caching and optimization
5. **Week 9**: Full validation and testing

### Rollback Plan
- Feature flags to disable new features
- Keep old endpoints functional
- Database migrations reversible
- Event logging failures non-blocking

---

## Conclusion

This plan transforms the OpenRide matching system from a **stop-based** to a **hub-based** architecture with comprehensive analytics and ML readiness. The phased approach ensures minimal disruption while achieving 100% alignment with the data.txt specification.

**Current**: 65% alignment  
**Target**: 100% alignment  
**Timeline**: 9 weeks  
**Risk**: Medium (database migrations, backward compatibility)  
**ROI**: High (ML capabilities, better metrics, scalable architecture)

---

**Next Steps**:
1. Review and approve this plan
2. Set up project tracking (Jira/Linear)
3. Assign engineers to phases
4. Begin Phase 1.1 (Hubs Infrastructure)

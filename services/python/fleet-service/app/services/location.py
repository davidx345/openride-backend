"""Location tracking service with PostGIS support"""
import json
from datetime import datetime, timedelta
from decimal import Decimal
from typing import Dict, Any, List, Optional
from uuid import UUID

from geoalchemy2.functions import ST_Distance, ST_DWithin, ST_GeogFromText, ST_MakePoint
from sqlalchemy import and_, or_, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.logging import get_logger
from app.db.models import DriverLocation, LocationHistory
from app.schemas.location import DriverLocationUpdate, NearbyDriverResponse

logger = get_logger(__name__)


class RateLimitError(Exception):
    """Rate limit exceeded exception"""
    pass


class LocationService:
    """Handles driver location tracking and queries"""
    
    def __init__(self, db: AsyncSession, redis, sio):
        self.db = db
        self.redis = redis
        self.sio = sio
    
    async def update_driver_location(
        self,
        driver_id: UUID,
        latitude: Decimal,
        longitude: Decimal,
        bearing: Optional[Decimal] = None,
        speed: Optional[Decimal] = None,
        accuracy: Optional[Decimal] = None,
        altitude: Optional[Decimal] = None,
    ) -> DriverLocation:
        """Update driver's current location
        
        Args:
            driver_id: Driver UUID
            latitude: Latitude in decimal degrees
            longitude: Longitude in decimal degrees
            bearing: Direction in degrees (0-360)
            speed: Speed in km/h
            accuracy: GPS accuracy in meters
            altitude: Altitude in meters
            
        Returns:
            Updated DriverLocation instance
            
        Raises:
            RateLimitError: If rate limit is exceeded
        """
        # Check rate limit
        rate_limit_key = f"location:ratelimit:{driver_id}"
        if await self.redis.exists(rate_limit_key):
            raise RateLimitError(
                f"Location update rate limit: {settings.LOCATION_UPDATE_RATE_LIMIT}s between updates"
            )
        
        # Create PostGIS point geometry
        location_wkt = f"POINT({longitude} {latitude})"
        
        # Get existing location record
        result = await self.db.execute(
            select(DriverLocation).where(DriverLocation.driver_id == driver_id)
        )
        location = result.scalar_one_or_none()
        
        if location:
            # Update existing record
            location.latitude = latitude
            location.longitude = longitude
            location.bearing = bearing
            location.speed = speed
            location.accuracy = accuracy
            location.altitude = altitude
            location.updated_at = datetime.utcnow()
            
            # Update PostGIS geometry
            await self.db.execute(
                update(DriverLocation)
                .where(DriverLocation.driver_id == driver_id)
                .values(location=ST_GeogFromText(location_wkt))
            )
        else:
            # Create new record
            location = DriverLocation(
                driver_id=driver_id,
                latitude=latitude,
                longitude=longitude,
                bearing=bearing,
                speed=speed,
                accuracy=accuracy,
                altitude=altitude,
                status='ONLINE',
            )
            self.db.add(location)
            
            # Set PostGIS geometry
            await self.db.execute(
                update(DriverLocation)
                .where(DriverLocation.driver_id == driver_id)
                .values(location=ST_GeogFromText(location_wkt))
            )
        
        await self.db.commit()
        await self.db.refresh(location)
        
        # Set rate limit
        await self.redis.setex(
            rate_limit_key,
            settings.LOCATION_UPDATE_RATE_LIMIT,
            '1',
        )
        
        # Publish to Redis for real-time broadcasting
        await self._publish_location_update(driver_id, location)
        
        logger.info(
            f"Location updated: driver_id={driver_id}, lat={latitude}, lon={longitude}"
        )
        
        return location
    
    async def update_driver_status(
        self,
        driver_id: UUID,
        status: str,
        location_data: Optional[Dict[str, Any]] = None,
    ) -> None:
        """Update driver status (ONLINE, OFFLINE, BUSY, ON_TRIP)
        
        Args:
            driver_id: Driver UUID
            status: New status
            location_data: Optional location data to update
        """
        # Update status
        await self.db.execute(
            update(DriverLocation)
            .where(DriverLocation.driver_id == driver_id)
            .values(status=status, updated_at=datetime.utcnow())
        )
        
        # Update location if provided
        if location_data and status in ['ONLINE', 'BUSY', 'ON_TRIP']:
            await self.update_driver_location(
                driver_id=driver_id,
                **location_data,
            )
        
        await self.db.commit()
        
        # Publish status change
        await self._publish_status_update(driver_id, status)
        
        logger.info(f"Driver status updated: driver_id={driver_id}, status={status}")
    
    async def get_driver_location(self, driver_id: UUID) -> Optional[DriverLocation]:
        """Get driver's current location"""
        result = await self.db.execute(
            select(DriverLocation).where(DriverLocation.driver_id == driver_id)
        )
        return result.scalar_one_or_none()
    
    async def find_nearby_drivers(
        self,
        latitude: Decimal,
        longitude: Decimal,
        radius_meters: int = 5000,
        limit: int = 10,
    ) -> List[NearbyDriverResponse]:
        """Find drivers within radius using PostGIS
        
        Args:
            latitude: Search center latitude
            longitude: Search center longitude
            radius_meters: Search radius in meters
            limit: Maximum number of results
            
        Returns:
            List of nearby drivers with distance
        """
        # Create search point
        search_point = f"POINT({longitude} {latitude})"
        
        # Query using PostGIS ST_DWithin for efficiency
        query = select(
            DriverLocation,
            ST_Distance(
                DriverLocation.location,
                ST_GeogFromText(search_point),
            ).label('distance_meters')
        ).where(
            and_(
                DriverLocation.status.in_(['ONLINE', 'BUSY']),
                ST_DWithin(
                    DriverLocation.location,
                    ST_GeogFromText(search_point),
                    radius_meters,
                ),
            )
        ).order_by(
            ST_Distance(
                DriverLocation.location,
                ST_GeogFromText(search_point),
            )
        ).limit(limit)
        
        result = await self.db.execute(query)
        
        nearby_drivers = []
        for row in result:
            location = row[0]
            distance = int(row[1])
            
            nearby_drivers.append(
                NearbyDriverResponse(
                    driver_id=location.driver_id,
                    latitude=location.latitude,
                    longitude=location.longitude,
                    bearing=location.bearing,
                    distance_meters=distance,
                    status=location.status,
                    updated_at=location.updated_at,
                )
            )
        
        logger.info(
            f"Found {len(nearby_drivers)} drivers within {radius_meters}m "
            f"of ({latitude}, {longitude})"
        )
        
        return nearby_drivers
    
    async def get_location_history(
        self,
        driver_id: UUID,
        trip_id: Optional[UUID] = None,
        start_time: Optional[datetime] = None,
        end_time: Optional[datetime] = None,
        limit: int = 100,
    ) -> List[LocationHistory]:
        """Get driver's location history
        
        Args:
            driver_id: Driver UUID
            trip_id: Optional trip UUID to filter by
            start_time: Optional start time filter
            end_time: Optional end time filter
            limit: Maximum number of results
            
        Returns:
            List of historical location records
        """
        query = select(LocationHistory).where(
            LocationHistory.driver_id == driver_id
        )
        
        if trip_id:
            query = query.where(LocationHistory.trip_id == trip_id)
        
        if start_time:
            query = query.where(LocationHistory.recorded_at >= start_time)
        
        if end_time:
            query = query.where(LocationHistory.recorded_at <= end_time)
        
        query = query.order_by(LocationHistory.recorded_at.desc()).limit(limit)
        
        result = await self.db.execute(query)
        return result.scalars().all()
    
    async def archive_location(
        self,
        driver_id: UUID,
        trip_id: Optional[UUID],
        location: DriverLocation,
    ) -> None:
        """Archive current location to history"""
        history = LocationHistory(
            driver_id=driver_id,
            trip_id=trip_id,
            latitude=location.latitude,
            longitude=location.longitude,
            bearing=location.bearing,
            speed=location.speed,
            accuracy=location.accuracy,
            recorded_at=location.updated_at,
        )
        
        # Copy PostGIS geometry
        await self.db.execute(
            update(LocationHistory)
            .where(LocationHistory.id == history.id)
            .values(location=location.location)
        )
        
        self.db.add(history)
        await self.db.commit()
    
    async def broadcast_location_update(
        self,
        driver_id: UUID,
        location: DriverLocation,
    ) -> None:
        """Broadcast location update to subscribers via Socket.IO"""
        data = {
            'driver_id': str(driver_id),
            'latitude': float(location.latitude),
            'longitude': float(location.longitude),
            'bearing': float(location.bearing) if location.bearing else None,
            'speed': float(location.speed) if location.speed else None,
            'accuracy': float(location.accuracy) if location.accuracy else None,
            'status': location.status,
            'timestamp': location.updated_at.isoformat(),
        }
        
        # Emit to driver's room
        await self.sio.emit('driver:location', data, room=f"user:{driver_id}")
    
    async def _publish_location_update(
        self,
        driver_id: UUID,
        location: DriverLocation,
    ) -> None:
        """Publish location update to Redis pub/sub"""
        message = {
            'event': 'location:update',
            'driver_id': str(driver_id),
            'latitude': float(location.latitude),
            'longitude': float(location.longitude),
            'bearing': float(location.bearing) if location.bearing else None,
            'speed': float(location.speed) if location.speed else None,
            'status': location.status,
            'timestamp': location.updated_at.isoformat(),
        }
        
        await self.redis.publish(
            'fleet:location:updates',
            json.dumps(message),
        )
    
    async def _publish_status_update(self, driver_id: UUID, status: str) -> None:
        """Publish status update to Redis pub/sub"""
        message = {
            'event': 'status:update',
            'driver_id': str(driver_id),
            'status': status,
            'timestamp': datetime.utcnow().isoformat(),
        }
        
        await self.redis.publish(
            'fleet:status:updates',
            json.dumps(message),
        )

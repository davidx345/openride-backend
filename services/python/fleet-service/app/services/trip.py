"""Trip tracking and lifecycle management"""
import json
from datetime import datetime
from decimal import Decimal
from typing import Dict, Any, List, Optional
from uuid import UUID

from geoalchemy2.functions import ST_Distance, ST_GeogFromText
from sqlalchemy import and_, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.logging import get_logger
from app.db.models import TripTracking
from app.schemas.trip import TripCreate, TripStatusUpdate

logger = get_logger(__name__)


class TripService:
    """Handles trip tracking and status management"""
    
    def __init__(self, db: AsyncSession, redis, sio):
        self.db = db
        self.redis = redis
        self.sio = sio
    
    async def create_trip(self, trip_data: TripCreate) -> TripTracking:
        """Create a new trip tracking record
        
        Args:
            trip_data: Trip creation data
            
        Returns:
            Created TripTracking instance
        """
        # Create PostGIS points
        pickup_wkt = f"POINT({trip_data.pickup_location.longitude} {trip_data.pickup_location.latitude})"
        dropoff_wkt = f"POINT({trip_data.dropoff_location.longitude} {trip_data.dropoff_location.latitude})"
        
        trip = TripTracking(
            trip_id=trip_data.trip_id,
            booking_id=trip_data.booking_id,
            driver_id=trip_data.driver_id,
            rider_id=trip_data.rider_id,
            pickup_latitude=trip_data.pickup_location.latitude,
            pickup_longitude=trip_data.pickup_location.longitude,
            dropoff_latitude=trip_data.dropoff_location.latitude,
            dropoff_longitude=trip_data.dropoff_location.longitude,
            scheduled_time=trip_data.scheduled_time,
            status='PENDING',
        )
        
        self.db.add(trip)
        
        # Set PostGIS geometries
        await self.db.execute(
            update(TripTracking)
            .where(TripTracking.trip_id == trip_data.trip_id)
            .values(
                pickup_location=ST_GeogFromText(pickup_wkt),
                dropoff_location=ST_GeogFromText(dropoff_wkt),
            )
        )
        
        await self.db.commit()
        await self.db.refresh(trip)
        
        logger.info(f"Trip created: trip_id={trip.trip_id}, booking_id={trip.booking_id}")
        
        return trip
    
    async def update_trip_status(
        self,
        trip_id: UUID,
        status: str,
        estimated_arrival: Optional[datetime] = None,
    ) -> TripTracking:
        """Update trip status and trigger transitions
        
        Args:
            trip_id: Trip UUID
            status: New status
            estimated_arrival: Optional ETA
            
        Returns:
            Updated TripTracking instance
        """
        trip = await self.get_trip(trip_id)
        if not trip:
            raise ValueError(f"Trip not found: {trip_id}")
        
        # Validate status transition
        self._validate_status_transition(trip.status, status)
        
        # Update timestamps based on status
        update_values = {
            'status': status,
            'updated_at': datetime.utcnow(),
        }
        
        if estimated_arrival:
            update_values['estimated_arrival'] = estimated_arrival
        
        if status == 'EN_ROUTE' and not trip.started_at:
            update_values['started_at'] = datetime.utcnow()
        elif status == 'ARRIVED' and not trip.arrived_at:
            update_values['arrived_at'] = datetime.utcnow()
        elif status == 'IN_PROGRESS' and not trip.pickup_time:
            update_values['pickup_time'] = datetime.utcnow()
        elif status == 'COMPLETED' and not trip.completed_at:
            update_values['completed_at'] = datetime.utcnow()
            # Calculate metrics
            if trip.started_at:
                duration = (datetime.utcnow() - trip.started_at).total_seconds()
                update_values['duration_seconds'] = int(duration)
        elif status == 'CANCELLED' and not trip.cancelled_at:
            update_values['cancelled_at'] = datetime.utcnow()
        
        await self.db.execute(
            update(TripTracking)
            .where(TripTracking.trip_id == trip_id)
            .values(**update_values)
        )
        await self.db.commit()
        await self.db.refresh(trip)
        
        # Broadcast update to subscribers
        await self._broadcast_trip_update(trip)
        
        logger.info(f"Trip status updated: trip_id={trip_id}, status={status}")
        
        return trip
    
    async def get_trip(self, trip_id: UUID) -> Optional[TripTracking]:
        """Get trip by ID"""
        result = await self.db.execute(
            select(TripTracking).where(TripTracking.trip_id == trip_id)
        )
        return result.scalar_one_or_none()
    
    async def get_driver_active_trip(self, driver_id: UUID) -> Optional[TripTracking]:
        """Get driver's currently active trip"""
        result = await self.db.execute(
            select(TripTracking).where(
                and_(
                    TripTracking.driver_id == driver_id,
                    TripTracking.status.in_(['PENDING', 'EN_ROUTE', 'ARRIVED', 'IN_PROGRESS']),
                )
            )
        )
        return result.scalar_one_or_none()
    
    async def get_rider_trips(
        self,
        rider_id: UUID,
        status: Optional[str] = None,
        limit: int = 50,
        offset: int = 0,
    ) -> List[TripTracking]:
        """Get trips for a rider"""
        query = select(TripTracking).where(TripTracking.rider_id == rider_id)
        
        if status:
            query = query.where(TripTracking.status == status)
        
        query = query.order_by(TripTracking.created_at.desc()).limit(limit).offset(offset)
        
        result = await self.db.execute(query)
        return result.scalars().all()
    
    async def get_driver_trips(
        self,
        driver_id: UUID,
        status: Optional[str] = None,
        limit: int = 50,
        offset: int = 0,
    ) -> List[TripTracking]:
        """Get trips for a driver"""
        query = select(TripTracking).where(TripTracking.driver_id == driver_id)
        
        if status:
            query = query.where(TripTracking.status == status)
        
        query = query.order_by(TripTracking.created_at.desc()).limit(limit).offset(offset)
        
        result = await self.db.execute(query)
        return result.scalars().all()
    
    async def calculate_trip_distance(self, trip_id: UUID) -> Optional[int]:
        """Calculate trip distance using PostGIS
        
        Returns:
            Distance in meters
        """
        trip = await self.get_trip(trip_id)
        if not trip:
            return None
        
        # Use PostGIS to calculate distance
        query = select(
            ST_Distance(
                trip.pickup_location,
                trip.dropoff_location,
            ).label('distance')
        ).where(TripTracking.trip_id == trip_id)
        
        result = await self.db.execute(query)
        row = result.first()
        
        if row:
            distance_meters = int(row[0])
            
            # Update trip record
            await self.db.execute(
                update(TripTracking)
                .where(TripTracking.trip_id == trip_id)
                .values(distance_meters=distance_meters)
            )
            await self.db.commit()
            
            return distance_meters
        
        return None
    
    def _validate_status_transition(self, current: str, new: str) -> None:
        """Validate status transition is allowed"""
        valid_transitions = {
            'PENDING': ['EN_ROUTE', 'CANCELLED'],
            'EN_ROUTE': ['ARRIVED', 'CANCELLED'],
            'ARRIVED': ['IN_PROGRESS', 'CANCELLED'],
            'IN_PROGRESS': ['COMPLETED', 'CANCELLED'],
            'COMPLETED': [],
            'CANCELLED': [],
        }
        
        if new not in valid_transitions.get(current, []):
            raise ValueError(
                f"Invalid status transition: {current} -> {new}"
            )
    
    async def _broadcast_trip_update(self, trip: TripTracking) -> None:
        """Broadcast trip update to subscribers"""
        data = {
            'trip_id': str(trip.trip_id),
            'status': trip.status,
            'estimated_arrival': trip.estimated_arrival.isoformat() if trip.estimated_arrival else None,
            'timestamp': datetime.utcnow().isoformat(),
        }
        
        # Emit to trip room (riders subscribed)
        await self.sio.emit('trip:update', data, room=f"trip:{trip.trip_id}")
        
        # Publish to Redis
        message = {
            'event': 'trip:update',
            **data,
        }
        await self.redis.publish(
            'fleet:trip:updates',
            json.dumps(message),
        )

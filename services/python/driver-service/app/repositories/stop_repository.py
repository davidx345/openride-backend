"""
Stop repository for database operations.
"""
from typing import List, Optional
from uuid import UUID
from decimal import Decimal

from sqlalchemy import select, func, text
from sqlalchemy.ext.asyncio import AsyncSession
from geoalchemy2.elements import WKTElement

from app.models.stop import Stop
from app.schemas.stop import StopCreate


class StopRepository:
    """Repository for stop database operations."""
    
    def __init__(self, db: AsyncSession):
        """
        Initialize repository with database session.
        
        Args:
            db: Async database session
        """
        self.db = db
    
    async def create(self, stop_data: StopCreate) -> Stop:
        """
        Create a new stop.
        
        Args:
            stop_data: Stop creation data
            
        Returns:
            Created stop
        """
        # Create PostGIS point geometry
        point = WKTElement(f'POINT({stop_data.lon} {stop_data.lat})', srid=4326)
        
        stop = Stop(
            **stop_data.model_dump(),
            location=point
        )
        self.db.add(stop)
        await self.db.commit()
        await self.db.refresh(stop)
        return stop
    
    async def get_by_id(self, stop_id: UUID) -> Optional[Stop]:
        """
        Get stop by ID.
        
        Args:
            stop_id: Stop ID
            
        Returns:
            Stop if found, None otherwise
        """
        result = await self.db.execute(
            select(Stop).where(Stop.id == stop_id)
        )
        return result.scalar_one_or_none()
    
    async def get_by_coordinates(
        self,
        lat: Decimal,
        lon: Decimal
    ) -> Optional[Stop]:
        """
        Get stop by exact coordinates.
        
        Args:
            lat: Latitude
            lon: Longitude
            
        Returns:
            Stop if found, None otherwise
        """
        result = await self.db.execute(
            select(Stop).where(Stop.lat == lat, Stop.lon == lon)
        )
        return result.scalar_one_or_none()
    
    async def find_nearby(
        self,
        lat: Decimal,
        lon: Decimal,
        radius_meters: float = 10.0
    ) -> Optional[Stop]:
        """
        Find stop within radius using PostGIS.
        
        Args:
            lat: Latitude
            lon: Longitude
            radius_meters: Search radius in meters
            
        Returns:
            Nearest stop within radius, None if not found
        """
        # Use PostGIS ST_DWithin for efficient proximity search
        point = WKTElement(f'POINT({lon} {lat})', srid=4326)
        
        query = select(Stop).where(
            func.ST_DWithin(
                Stop.location,
                point,
                radius_meters / 111320.0  # Convert meters to degrees (approximate)
            )
        ).order_by(
            func.ST_Distance(Stop.location, point)
        ).limit(1)
        
        result = await self.db.execute(query)
        return result.scalar_one_or_none()
    
    async def search_by_proximity(
        self,
        lat: Decimal,
        lon: Decimal,
        radius_km: float = 5.0,
        limit: int = 20
    ) -> List[Stop]:
        """
        Search stops by proximity.
        
        Args:
            lat: Latitude
            lon: Longitude
            radius_km: Search radius in kilometers
            limit: Maximum number of results
            
        Returns:
            List of stops within radius, ordered by distance
        """
        point = WKTElement(f'POINT({lon} {lat})', srid=4326)
        radius_degrees = radius_km / 111.32  # Approximate conversion
        
        query = select(Stop).where(
            func.ST_DWithin(Stop.location, point, radius_degrees)
        ).order_by(
            func.ST_Distance(Stop.location, point)
        ).limit(limit)
        
        result = await self.db.execute(query)
        return list(result.scalars().all())
    
    async def get_or_create(
        self,
        stop_data: StopCreate,
        dedup_radius_meters: float = 10.0
    ) -> tuple[Stop, bool]:
        """
        Get existing stop or create new one if not found within radius.
        
        Args:
            stop_data: Stop data
            dedup_radius_meters: Deduplication radius in meters
            
        Returns:
            Tuple of (stop, created) where created is True if new stop was created
        """
        # Check if stop exists nearby
        existing = await self.find_nearby(
            stop_data.lat,
            stop_data.lon,
            dedup_radius_meters
        )
        
        if existing:
            return existing, False
        
        # Create new stop
        new_stop = await self.create(stop_data)
        return new_stop, True

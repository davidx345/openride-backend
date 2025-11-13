"""
Stop service for business logic.
"""
import logging
from typing import List, Optional
from uuid import UUID

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import StopNotFoundException
from app.core.config import settings
from app.models.stop import Stop
from app.repositories.stop_repository import StopRepository
from app.schemas.stop import StopCreate

logger = logging.getLogger(__name__)


class StopService:
    """Service for stop business logic."""
    
    def __init__(self, db: AsyncSession):
        """
        Initialize service with database session.
        
        Args:
            db: Async database session
        """
        self.db = db
        self.repo = StopRepository(db)
    
    async def create_or_get_stop(self, stop_data: StopCreate) -> tuple[Stop, bool]:
        """
        Create stop or get existing one if within deduplication radius.
        
        Args:
            stop_data: Stop creation data
            
        Returns:
            Tuple of (stop, created) where created is True if new stop was created
        """
        stop, created = await self.repo.get_or_create(
            stop_data,
            settings.STOP_DEDUP_RADIUS_METERS
        )
        
        if created:
            logger.info(f"Created new stop {stop.id}: {stop.name}")
        else:
            logger.info(f"Using existing stop {stop.id}: {stop.name}")
        
        return stop, created
    
    async def get_stop(self, stop_id: UUID) -> Stop:
        """
        Get stop by ID.
        
        Args:
            stop_id: Stop ID
            
        Returns:
            Stop
            
        Raises:
            StopNotFoundException: If stop not found
        """
        stop = await self.repo.get_by_id(stop_id)
        
        if not stop:
            raise StopNotFoundException(f"Stop {stop_id} not found")
        
        return stop
    
    async def search_stops(
        self,
        lat: float,
        lon: float,
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
            List of stops within radius
        """
        return await self.repo.search_by_proximity(lat, lon, radius_km, limit)

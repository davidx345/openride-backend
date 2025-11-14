"""
Hub repository for database operations.
"""
import logging
from typing import List, Optional
from uuid import UUID

from sqlalchemy import and_, select
from sqlalchemy.ext.asyncio import AsyncSession
from geoalchemy2.functions import ST_Distance, ST_DWithin, ST_MakePoint, ST_SetSRID
from sqlalchemy import cast
from sqlalchemy.types import Geography

from app.models.hub import Hub

logger = logging.getLogger(__name__)


class HubRepository:
    """Repository for hub database operations."""
    
    def __init__(self, db: AsyncSession):
        """
        Initialize repository with database session.
        
        Args:
            db: Async database session
        """
        self.db = db
    
    async def get_by_id(self, hub_id: UUID) -> Optional[Hub]:
        """
        Get hub by ID.
        
        Args:
            hub_id: Hub ID
            
        Returns:
            Hub if found, None otherwise
        """
        stmt = select(Hub).where(Hub.id == hub_id)
        result = await self.db.execute(stmt)
        return result.scalar_one_or_none()
    
    async def get_all_active(self) -> List[Hub]:
        """
        Get all active hubs.
        
        Returns:
            List of active hubs
        """
        stmt = select(Hub).where(Hub.is_active == True).order_by(Hub.name)
        result = await self.db.execute(stmt)
        return list(result.scalars().all())
    
    async def get_by_area(self, area_id: str, active_only: bool = True) -> List[Hub]:
        """
        Get hubs by area ID.
        
        Args:
            area_id: Area identifier
            active_only: Only return active hubs
            
        Returns:
            List of hubs in area
        """
        conditions = [Hub.area_id == area_id]
        if active_only:
            conditions.append(Hub.is_active == True)
        
        stmt = select(Hub).where(and_(*conditions)).order_by(Hub.name)
        result = await self.db.execute(stmt)
        return list(result.scalars().all())
    
    async def get_by_zone(self, zone: str, active_only: bool = True) -> List[Hub]:
        """
        Get hubs by zone.
        
        Args:
            zone: Zone grouping (e.g., 'Island', 'Mainland')
            active_only: Only return active hubs
            
        Returns:
            List of hubs in zone
        """
        conditions = [Hub.zone == zone]
        if active_only:
            conditions.append(Hub.is_active == True)
        
        stmt = select(Hub).where(and_(*conditions)).order_by(Hub.name)
        result = await self.db.execute(stmt)
        return list(result.scalars().all())
    
    async def find_nearest_hub(
        self,
        lat: float,
        lon: float,
        radius_meters: float = 1000.0,
        active_only: bool = True
    ) -> Optional[Hub]:
        """
        Find nearest active hub within radius.
        
        Args:
            lat: Latitude
            lon: Longitude
            radius_meters: Search radius in meters (default 1km)
            active_only: Only search active hubs
            
        Returns:
            Nearest hub if found within radius, None otherwise
        """
        point = cast(ST_SetSRID(ST_MakePoint(lon, lat), 4326), Geography)
        
        conditions = [
            ST_DWithin(cast(Hub.location, Geography), point, radius_meters)
        ]
        if active_only:
            conditions.append(Hub.is_active == True)
        
        stmt = (
            select(Hub)
            .where(and_(*conditions))
            .order_by(ST_Distance(cast(Hub.location, Geography), point))
            .limit(1)
        )
        
        result = await self.db.execute(stmt)
        return result.scalar_one_or_none()
    
    async def find_hubs_within_radius(
        self,
        lat: float,
        lon: float,
        radius_meters: float = 5000.0,
        active_only: bool = True,
        limit: int = 10
    ) -> List[Hub]:
        """
        Find all hubs within radius, ordered by distance.
        
        Args:
            lat: Latitude
            lon: Longitude
            radius_meters: Search radius in meters (default 5km)
            active_only: Only return active hubs
            limit: Maximum number of results
            
        Returns:
            List of hubs within radius, sorted by distance
        """
        point = cast(ST_SetSRID(ST_MakePoint(lon, lat), 4326), Geography)
        
        conditions = [
            ST_DWithin(cast(Hub.location, Geography), point, radius_meters)
        ]
        if active_only:
            conditions.append(Hub.is_active == True)
        
        stmt = (
            select(Hub)
            .where(and_(*conditions))
            .order_by(ST_Distance(cast(Hub.location, Geography), point))
            .limit(limit)
        )
        
        result = await self.db.execute(stmt)
        return list(result.scalars().all())
    
    async def create(self, hub: Hub) -> Hub:
        """
        Create a new hub.
        
        Args:
            hub: Hub to create
            
        Returns:
            Created hub
        """
        self.db.add(hub)
        await self.db.commit()
        await self.db.refresh(hub)
        logger.info(f"Created hub: {hub.name} ({hub.id})")
        return hub
    
    async def update(self, hub: Hub) -> Hub:
        """
        Update existing hub.
        
        Args:
            hub: Hub to update
            
        Returns:
            Updated hub
        """
        await self.db.commit()
        await self.db.refresh(hub)
        logger.info(f"Updated hub: {hub.name} ({hub.id})")
        return hub
    
    async def deactivate(self, hub_id: UUID) -> bool:
        """
        Deactivate a hub (soft delete).
        
        Args:
            hub_id: Hub ID to deactivate
            
        Returns:
            True if deactivated, False if not found
        """
        hub = await self.get_by_id(hub_id)
        if not hub:
            return False
        
        hub.is_active = False
        await self.db.commit()
        logger.info(f"Deactivated hub: {hub.name} ({hub.id})")
        return True
    
    async def activate(self, hub_id: UUID) -> bool:
        """
        Activate a hub.
        
        Args:
            hub_id: Hub ID to activate
            
        Returns:
            True if activated, False if not found
        """
        hub = await self.get_by_id(hub_id)
        if not hub:
            return False
        
        hub.is_active = True
        await self.db.commit()
        logger.info(f"Activated hub: {hub.name} ({hub.id})")
        return True

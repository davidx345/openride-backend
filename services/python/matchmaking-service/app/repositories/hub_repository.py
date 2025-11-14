"""Hub repository for matchmaking service."""

from typing import Sequence
from uuid import UUID

from geoalchemy2.functions import ST_Distance, ST_DWithin, ST_MakePoint, ST_SetSRID
from sqlalchemy import and_, cast, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.types import Geography

from app.models.hub import Hub


class HubRepository:
    """Repository for hub database operations."""

    def __init__(self, db: AsyncSession):
        """
        Initialize repository with database session.

        Args:
            db: Async database session
        """
        self.db = db

    async def get_by_id(self, hub_id: UUID) -> Hub | None:
        """
        Get hub by ID.

        Args:
            hub_id: Hub ID

        Returns:
            Hub | None: Hub if found
        """
        stmt = select(Hub).where(Hub.id == hub_id)
        result = await self.db.execute(stmt)
        return result.scalar_one_or_none()

    async def get_all_active(self) -> Sequence[Hub]:
        """
        Get all active hubs.

        Returns:
            Sequence[Hub]: List of active hubs
        """
        stmt = select(Hub).where(Hub.is_active == True).order_by(Hub.name)
        result = await self.db.execute(stmt)
        return result.scalars().all()

    async def find_nearest_hub(
        self,
        lat: float,
        lon: float,
        radius_meters: float = 1000.0,
    ) -> Hub | None:
        """
        Find nearest active hub within radius.

        Uses PostGIS ST_DWithin for efficient spatial query.

        Args:
            lat: Latitude
            lon: Longitude
            radius_meters: Search radius in meters (default 1km)

        Returns:
            Hub | None: Nearest hub if found within radius
        """
        point = cast(ST_SetSRID(ST_MakePoint(lon, lat), 4326), Geography)

        stmt = (
            select(Hub)
            .where(
                and_(
                    Hub.is_active == True,
                    ST_DWithin(cast(Hub.location, Geography), point, radius_meters),
                )
            )
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
        limit: int = 10,
    ) -> Sequence[Hub]:
        """
        Find all active hubs within radius, ordered by distance.

        Args:
            lat: Latitude
            lon: Longitude
            radius_meters: Search radius in meters (default 5km)
            limit: Maximum number of results

        Returns:
            Sequence[Hub]: List of hubs within radius, sorted by distance
        """
        point = cast(ST_SetSRID(ST_MakePoint(lon, lat), 4326), Geography)

        stmt = (
            select(Hub)
            .where(
                and_(
                    Hub.is_active == True,
                    ST_DWithin(cast(Hub.location, Geography), point, radius_meters),
                )
            )
            .order_by(ST_Distance(cast(Hub.location, Geography), point))
            .limit(limit)
        )

        result = await self.db.execute(stmt)
        return result.scalars().all()

    async def get_by_area(self, area_id: str) -> Sequence[Hub]:
        """
        Get active hubs by area ID.

        Args:
            area_id: Area identifier

        Returns:
            Sequence[Hub]: List of hubs in area
        """
        stmt = (
            select(Hub)
            .where(and_(Hub.area_id == area_id, Hub.is_active == True))
            .order_by(Hub.name)
        )
        result = await self.db.execute(stmt)
        return result.scalars().all()

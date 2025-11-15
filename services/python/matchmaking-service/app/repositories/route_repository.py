"""Route repository with geospatial queries."""

import logging
from datetime import time
from typing import Optional, Sequence
from uuid import UUID

from geoalchemy2.functions import ST_Distance, ST_DWithin, ST_MakePoint, ST_SetSRID
from sqlalchemy import and_, cast, func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload
from sqlalchemy.types import Geography

from app.models.route import Route, RouteStatus
from app.models.route_stop import RouteStop
from app.models.stop import Stop

logger = logging.getLogger(__name__)

class RouteRepository:
    """Repository for route database operations."""

    def __init__(self, db: AsyncSession):
        """
        Initialize repository with database session.

        Args:
            db: Async database session
        """
        self.db = db

    async def find_nearby_routes(
        self,
        origin_lat: float,
        origin_lon: float,
        dest_lat: float | None,
        dest_lon: float | None,
        radius_meters: float,
        max_results: int = 50,
    ) -> Sequence[Route]:
        """
        Find active routes with stops near origin and/or destination.

        Uses PostGIS ST_DWithin for efficient spatial queries.

        Args:
            origin_lat: Origin latitude
            origin_lon: Origin longitude
            dest_lat: Destination latitude (optional)
            dest_lon: Destination longitude (optional)
            radius_meters: Search radius in meters
            max_results: Maximum number of routes to return

        Returns:
            Sequence[Route]: List of matching routes with stops loaded
        """
        # Create point geometries
        origin_point = cast(
            ST_SetSRID(ST_MakePoint(origin_lon, origin_lat), 4326), Geography
        )

        # Base query for routes with stops near origin
        origin_condition = ST_DWithin(
            cast(Stop.location, Geography), origin_point, radius_meters
        )

        # Build conditions
        conditions = [origin_condition]

        # Add destination condition if provided
        if dest_lat is not None and dest_lon is not None:
            dest_point = cast(
                ST_SetSRID(ST_MakePoint(dest_lon, dest_lat), 4326), Geography
            )
            dest_condition = ST_DWithin(cast(Stop.location, Geography), dest_point, radius_meters)
            # Routes must have stops near BOTH origin AND destination
            conditions.append(dest_condition)

        # Query for distinct routes
        stmt = (
            select(Route)
            .distinct()
            .join(RouteStop, Route.id == RouteStop.route_id)
            .join(Stop, RouteStop.stop_id == Stop.id)
            .where(
                and_(
                    Route.status == RouteStatus.ACTIVE,
                    Route.seats_available > 0,
                    or_(*conditions),  # At least one condition must match
                )
            )
            .options(selectinload(Route.route_stops).selectinload(RouteStop.stop))
            .limit(max_results)
        )

        result = await self.db.execute(stmt)
        return result.scalars().unique().all()

    async def get_route_by_id(self, route_id: UUID) -> Route | None:
        """
        Get route by ID with stops loaded.

        Args:
            route_id: Route ID

        Returns:
            Route | None: Route or None if not found
        """
        stmt = (
            select(Route)
            .where(Route.id == route_id)
            .options(selectinload(Route.route_stops).selectinload(RouteStop.stop))
        )
        result = await self.db.execute(stmt)
        return result.scalar_one_or_none()

    async def get_active_routes(self, limit: int = 100) -> Sequence[Route]:
        """
        Get all active routes.

        Args:
            limit: Maximum number of routes

        Returns:
            Sequence[Route]: List of active routes
        """
        stmt = (
            select(Route)
            .where(and_(Route.status == RouteStatus.ACTIVE, Route.seats_available > 0))
            .limit(limit)
        )
        result = await self.db.execute(stmt)
        return result.scalars().all()

    async def filter_by_time_window(
        self,
        routes: Sequence[Route],
        desired_time: time,
        window_minutes: int = 15,
    ) -> list[Route]:
        """
        Filter routes by departure time window.

        Note: This is a Python-level filter, not a database query.
        Call this after get_nearby_routes for efficiency.

        Args:
            routes: List of routes to filter
            desired_time: Desired departure time
            window_minutes: Time window in minutes (±)

        Returns:
            list[Route]: Filtered routes
        """
        from datetime import datetime, timedelta

        # Convert time to minutes since midnight
        desired_minutes = desired_time.hour * 60 + desired_time.minute
        lower_bound = desired_minutes - window_minutes
        upper_bound = desired_minutes + window_minutes

        filtered = []
        for route in routes:
            route_minutes = route.departure_time.hour * 60 + route.departure_time.minute

            # Handle wrap-around at midnight
            if lower_bound < 0:
                if route_minutes >= (1440 + lower_bound) or route_minutes <= upper_bound:
                    filtered.append(route)
            elif upper_bound > 1440:
                if route_minutes >= lower_bound or route_minutes <= (upper_bound - 1440):
                    filtered.append(route)
            else:
                if lower_bound <= route_minutes <= upper_bound:
                    filtered.append(route)

        return filtered

    async def calculate_distance_to_routes(
        self,
        routes: Sequence[Route],
        origin_lat: float,
        origin_lon: float,
    ) -> dict[UUID, float]:
        """
        Calculate distance from origin to each route's nearest stop.

        Args:
            routes: List of routes
            origin_lat: Origin latitude
            origin_lon: Origin longitude

        Returns:
            dict[UUID, float]: Map of route_id -> distance in meters
        """
        if not routes:
            return {}

        origin_point = cast(
            ST_SetSRID(ST_MakePoint(origin_lon, origin_lat), 4326), Geography
        )

        route_ids = [route.id for route in routes]

        # Query minimum distance for each route
        stmt = (
            select(
                RouteStop.route_id,
                func.min(ST_Distance(cast(Stop.location, Geography), origin_point)).label(
                    "min_distance"
                ),
            )
            .join(Stop, RouteStop.stop_id == Stop.id)
            .where(RouteStop.route_id.in_(route_ids))
            .group_by(RouteStop.route_id)
        )

        result = await self.db.execute(stmt)
        distances = {row.route_id: float(row.min_distance) for row in result}
        return distances

    async def find_routes_by_hubs(
        self,
        origin_hub_id: Optional[UUID],
        destination_hub_id: Optional[UUID],
        active_only: bool = True,
    ) -> Sequence[Route]:
        """
        Find routes by hub pair.

        Args:
            origin_hub_id: Origin hub UUID
            destination_hub_id: Destination hub UUID
            active_only: Filter active routes only

        Returns:
            Sequence[Route]: List of matching routes
        """
        conditions = []

        if origin_hub_id:
            conditions.append(Route.origin_hub_id == origin_hub_id)
        if destination_hub_id:
            conditions.append(Route.destination_hub_id == destination_hub_id)
        if active_only:
            conditions.append(Route.status == RouteStatus.ACTIVE)
            conditions.append(Route.seats_available > 0)

        stmt = select(Route).where(and_(*conditions))

        result = await self.db.execute(stmt)
        return result.scalars().all()

    async def get_routes_by_ids(self, route_ids: list[UUID]) -> list[Route]:
        """
        Get routes by IDs.

        Args:
            route_ids: List of route UUIDs

        Returns:
            list[Route]: List of Route objects
        """
        stmt = (
            select(Route)
            .where(Route.id.in_(route_ids))
            .options(selectinload(Route.route_stops).selectinload(RouteStop.stop))
        )
        result = await self.db.execute(stmt)
        return list(result.scalars().all())

    async def get_driver_stats_batch(self, driver_ids: list[str]) -> list[dict]:
        """
        Get driver statistics from materialized view.

        Args:
            driver_ids: List of driver UUIDs as strings

        Returns:
            list[dict]: List of driver stats dicts
        """
        # Query from driver_stats_agg materialized view (created in Phase 2)
        # If view doesn't exist, return empty results
        try:
            from sqlalchemy import text

            query = text("""
                SELECT 
                    driver_id,
                    rating_avg,
                    rating_count,
                    cancellation_rate,
                    completed_trips,
                    acceptance_rate,
                    avg_response_time
                FROM driver_stats_agg
                WHERE driver_id = ANY(:driver_ids)
            """)

            result = await self.db.execute(query, {"driver_ids": driver_ids})
            rows = result.fetchall()

            stats = []
            for row in rows:
                stats.append({
                    "driver_id": str(row[0]),
                    "rating_avg": float(row[1]) if row[1] is not None else 0.0,
                    "rating_count": int(row[2]) if row[2] is not None else 0,
                    "cancellation_rate": float(row[3]) if row[3] is not None else 0.0,
                    "completed_trips": int(row[4]) if row[4] is not None else 0,
                    "acceptance_rate": float(row[5]) if row[5] is not None else 0.0,
                    "avg_response_time": int(row[6]) if row[6] is not None else 0,
                })

            return stats

        except Exception as e:
            logger.warning(f"Failed to fetch driver stats: {e}")
            return []

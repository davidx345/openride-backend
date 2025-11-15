"""Hub compatibility scoring service for route matching."""

import logging
from typing import Optional
from uuid import UUID

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.models.hub import Hub
from app.repositories.hub_repository import HubRepository

settings = get_settings()
logger = logging.getLogger(__name__)


class HubCompatibilityService:
    """Service for scoring hub compatibility in route matching."""

    def __init__(self, db: AsyncSession):
        """
        Initialize hub compatibility service.

        Args:
            db: Database session
        """
        self.db = db
        self.hub_repo = HubRepository(db)

    async def calculate_hub_match_score(
        self,
        origin_lat: float,
        origin_lon: float,
        dest_lat: Optional[float],
        dest_lon: Optional[float],
        route_origin_hub_id: Optional[UUID],
        route_dest_hub_id: Optional[UUID],
    ) -> tuple[float, dict]:
        """
        Calculate hub compatibility score for a route.

        Scoring Logic:
        - Both hubs match: 1.0 (perfect match)
        - Origin hub matches: 0.7
        - Destination hub matches: 0.5
        - No hub match but nearby: 0.3
        - No hub association: 0.1

        Args:
            origin_lat: Rider origin latitude
            origin_lon: Rider origin longitude
            dest_lat: Rider destination latitude (optional)
            dest_lon: Rider destination longitude (optional)
            route_origin_hub_id: Route's origin hub ID
            route_dest_hub_id: Route's destination hub ID

        Returns:
            tuple: (score: float 0-1, details: dict)
        """
        details = {
            "origin_hub_match": False,
            "dest_hub_match": False,
            "origin_hub_nearby": False,
            "dest_hub_nearby": False,
            "origin_distance_km": None,
            "dest_distance_km": None,
        }

        # If route has no hub associations, low score
        if not route_origin_hub_id and not route_dest_hub_id:
            logger.debug("Route has no hub associations")
            return 0.1, details

        origin_hub_match = False
        dest_hub_match = False
        origin_hub_nearby = False
        dest_hub_nearby = False

        # Check origin hub match
        if route_origin_hub_id:
            origin_hub = await self.hub_repo.get_by_id(route_origin_hub_id)
            if origin_hub:
                # Calculate distance from rider origin to route's origin hub
                distance_km = await self.hub_repo.calculate_distance(
                    lat=origin_lat,
                    lon=origin_lon,
                    hub_id=route_origin_hub_id,
                )
                details["origin_distance_km"] = distance_km

                # Consider "nearby" if within 2km
                if distance_km <= 2.0:
                    origin_hub_nearby = True
                    details["origin_hub_nearby"] = True

                # Consider "match" if within 500m
                if distance_km <= 0.5:
                    origin_hub_match = True
                    details["origin_hub_match"] = True

        # Check destination hub match
        if route_dest_hub_id and dest_lat and dest_lon:
            dest_hub = await self.hub_repo.get_by_id(route_dest_hub_id)
            if dest_hub:
                # Calculate distance from rider destination to route's dest hub
                distance_km = await self.hub_repo.calculate_distance(
                    lat=dest_lat,
                    lon=dest_lon,
                    hub_id=route_dest_hub_id,
                )
                details["dest_distance_km"] = distance_km

                # Consider "nearby" if within 2km
                if distance_km <= 2.0:
                    dest_hub_nearby = True
                    details["dest_hub_nearby"] = True

                # Consider "match" if within 500m
                if distance_km <= 0.5:
                    dest_hub_match = True
                    details["dest_hub_match"] = True

        # Calculate composite score
        score = self._calculate_composite_score(
            origin_hub_match=origin_hub_match,
            dest_hub_match=dest_hub_match,
            origin_hub_nearby=origin_hub_nearby,
            dest_hub_nearby=dest_hub_nearby,
        )

        logger.debug(
            f"Hub compatibility: score={score:.2f}, "
            f"origin_match={origin_hub_match}, dest_match={dest_hub_match}"
        )

        return score, details

    def _calculate_composite_score(
        self,
        origin_hub_match: bool,
        dest_hub_match: bool,
        origin_hub_nearby: bool,
        dest_hub_nearby: bool,
    ) -> float:
        """
        Calculate composite hub compatibility score.

        Scoring Matrix:
        - Both match: 1.0
        - Origin match only: 0.7
        - Destination match only: 0.5
        - Both nearby (not exact match): 0.4
        - Origin nearby only: 0.3
        - Destination nearby only: 0.2
        - No proximity: 0.1

        Args:
            origin_hub_match: Origin hub within 500m
            dest_hub_match: Dest hub within 500m
            origin_hub_nearby: Origin hub within 2km
            dest_hub_nearby: Dest hub within 2km

        Returns:
            float: Score 0-1
        """
        # Perfect match - both hubs within 500m
        if origin_hub_match and dest_hub_match:
            return 1.0

        # Origin matches exactly
        if origin_hub_match:
            if dest_hub_nearby:
                return 0.8  # Origin match + dest nearby
            return 0.7  # Origin match only

        # Destination matches exactly
        if dest_hub_match:
            if origin_hub_nearby:
                return 0.6  # Dest match + origin nearby
            return 0.5  # Dest match only

        # Both nearby (but not exact matches)
        if origin_hub_nearby and dest_hub_nearby:
            return 0.4

        # Only origin nearby
        if origin_hub_nearby:
            return 0.3

        # Only destination nearby
        if dest_hub_nearby:
            return 0.2

        # No proximity
        return 0.1

    async def get_hub_zone_match(
        self,
        origin_lat: float,
        origin_lon: float,
        dest_lat: Optional[float],
        dest_lon: Optional[float],
        route_origin_hub_id: Optional[UUID],
        route_dest_hub_id: Optional[UUID],
    ) -> dict:
        """
        Check if hubs are in the same zone (Island/Mainland).

        Args:
            origin_lat: Rider origin latitude
            origin_lon: Rider origin longitude
            dest_lat: Rider destination latitude
            dest_lon: Rider destination longitude
            route_origin_hub_id: Route's origin hub
            route_dest_hub_id: Route's destination hub

        Returns:
            dict: Zone match information
        """
        result = {
            "same_zone": False,
            "origin_zone": None,
            "dest_zone": None,
            "cross_zone_route": False,
        }

        if not route_origin_hub_id or not route_dest_hub_id:
            return result

        # Get hub details
        origin_hub = await self.hub_repo.get_by_id(route_origin_hub_id)
        dest_hub = await self.hub_repo.get_by_id(route_dest_hub_id)

        if not origin_hub or not dest_hub:
            return result

        result["origin_zone"] = origin_hub.zone
        result["dest_zone"] = dest_hub.zone

        # Check if same zone
        if origin_hub.zone and dest_hub.zone:
            result["same_zone"] = origin_hub.zone == dest_hub.zone
            result["cross_zone_route"] = origin_hub.zone != dest_hub.zone

        return result

    async def find_alternative_hubs(
        self,
        lat: float,
        lon: float,
        max_distance_km: float = 5.0,
        limit: int = 5,
    ) -> list[tuple[Hub, float]]:
        """
        Find alternative hubs near a location.

        Useful for suggesting nearby pickup/dropoff points.

        Args:
            lat: Latitude
            lon: Longitude
            max_distance_km: Maximum distance in kilometers
            limit: Maximum number of hubs to return

        Returns:
            list: List of (Hub, distance_km) tuples
        """
        hubs = await self.hub_repo.find_nearby_hubs(
            lat=lat,
            lon=lon,
            max_distance_km=max_distance_km,
            limit=limit,
        )

        # Calculate distances for each hub
        result = []
        for hub in hubs:
            distance_km = await self.hub_repo.calculate_distance(
                lat=lat,
                lon=lon,
                hub_id=hub.id,
            )
            result.append((hub, distance_km))

        # Sort by distance
        result.sort(key=lambda x: x[1])

        return result

    async def filter_compatible_routes(
        self,
        routes: list,
        origin_hub_id: UUID,
        destination_hub_id: UUID,
        min_compatibility_score: float = 0.3,
    ) -> list:
        """
        Filter routes by hub compatibility score.

        Args:
            routes: List of Route objects
            origin_hub_id: Origin hub UUID
            destination_hub_id: Destination hub UUID
            min_compatibility_score: Minimum score threshold (0-1)

        Returns:
            list: Filtered routes with compatible hubs
        """
        compatible_routes = []

        for route in routes:
            # Skip routes without hub associations
            if not route.origin_hub_id or not route.destination_hub_id:
                logger.debug(f"Route {route.id} has no hub associations, skipping")
                continue

            # Check if route matches the hub pair
            if route.origin_hub_id == origin_hub_id and route.destination_hub_id == destination_hub_id:
                compatible_routes.append(route)
                logger.debug(f"Route {route.id} matches hub pair exactly")
            elif route.origin_hub_id == origin_hub_id:
                # At least origin matches
                compatible_routes.append(route)
                logger.debug(f"Route {route.id} matches origin hub")

        return compatible_routes

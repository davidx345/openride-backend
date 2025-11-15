"""Stop sequence validation for route matching."""

import logging
from typing import Optional
from uuid import UUID

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.models.route import Route
from app.models.route_stop import RouteStop
from app.repositories.route_repository import RouteRepository

settings = get_settings()
logger = logging.getLogger(__name__)


class StopSequenceValidator:
    """Validates stop sequences for route matching."""

    def __init__(self, db: AsyncSession):
        """
        Initialize stop sequence validator.

        Args:
            db: Database session
        """
        self.db = db
        self.route_repo = RouteRepository(db)

    async def validate_stop_sequence(
        self,
        route: Route,
        origin_lat: float,
        origin_lon: float,
        dest_lat: Optional[float],
        dest_lon: Optional[float],
    ) -> tuple[bool, dict]:
        """
        Validate that a route's stop sequence matches rider's journey.

        Checks:
        1. Origin stop comes before destination stop in sequence
        2. Stops are reasonably close to rider's origin/destination
        3. Route direction is correct (not backtracking)

        Args:
            route: Route to validate
            origin_lat: Rider origin latitude
            origin_lon: Rider origin longitude
            dest_lat: Rider destination latitude (optional)
            dest_lon: Rider destination longitude (optional)

        Returns:
            tuple: (is_valid: bool, details: dict)
        """
        details = {
            "origin_stop_index": None,
            "dest_stop_index": None,
            "correct_direction": False,
            "origin_stop_distance_km": None,
            "dest_stop_distance_km": None,
            "sequence_valid": False,
        }

        # Get route stops (should be preloaded with route)
        if not hasattr(route, "route_stops") or not route.route_stops:
            logger.warning(f"Route {route.id} has no stops loaded")
            return False, details

        route_stops = sorted(route.route_stops, key=lambda rs: rs.stop_order)

        if len(route_stops) < 2:
            logger.debug(f"Route {route.id} has insufficient stops")
            return False, details

        # Find closest stop to origin
        origin_stop, origin_stop_idx, origin_distance = await self._find_closest_stop(
            route_stops=route_stops,
            lat=origin_lat,
            lon=origin_lon,
        )

        if not origin_stop:
            return False, details

        details["origin_stop_index"] = origin_stop_idx
        details["origin_stop_distance_km"] = origin_distance

        # If no destination provided, just check origin proximity
        if not dest_lat or not dest_lon:
            is_valid = origin_distance <= 2.0  # Within 2km
            details["sequence_valid"] = is_valid
            return is_valid, details

        # Find closest stop to destination
        dest_stop, dest_stop_idx, dest_distance = await self._find_closest_stop(
            route_stops=route_stops,
            lat=dest_lat,
            lon=dest_lon,
        )

        if not dest_stop:
            return False, details

        details["dest_stop_index"] = dest_stop_idx
        details["dest_stop_distance_km"] = dest_distance

        # Validate sequence: origin stop must come before destination stop
        if origin_stop_idx < dest_stop_idx:
            details["correct_direction"] = True
        else:
            logger.debug(
                f"Route {route.id}: Invalid sequence - "
                f"origin at {origin_stop_idx}, dest at {dest_stop_idx}"
            )
            return False, details

        # Validate proximity: both stops should be within reasonable distance
        # Origin: 2km, Destination: 2km
        if origin_distance > 2.0 or dest_distance > 2.0:
            logger.debug(
                f"Route {route.id}: Stops too far - "
                f"origin: {origin_distance:.2f}km, dest: {dest_distance:.2f}km"
            )
            return False, details

        # All checks passed
        details["sequence_valid"] = True
        return True, details

    async def _find_closest_stop(
        self,
        route_stops: list[RouteStop],
        lat: float,
        lon: float,
    ) -> tuple[Optional[RouteStop], Optional[int], Optional[float]]:
        """
        Find the closest stop to a location.

        Args:
            route_stops: List of RouteStop objects (sorted by stop_order)
            lat: Latitude
            lon: Longitude

        Returns:
            tuple: (closest_stop, stop_index, distance_km) or (None, None, None)
        """
        min_distance = float("inf")
        closest_stop = None
        closest_idx = None

        for idx, route_stop in enumerate(route_stops):
            if not route_stop.stop:
                continue

            # Calculate distance using PostGIS
            stop = route_stop.stop
            distance_km = self._calculate_haversine_distance(
                lat1=lat,
                lon1=lon,
                lat2=float(stop.lat),
                lon2=float(stop.lon),
            )

            if distance_km < min_distance:
                min_distance = distance_km
                closest_stop = route_stop
                closest_idx = idx

        if closest_stop:
            return closest_stop, closest_idx, min_distance

        return None, None, None

    def _calculate_haversine_distance(
        self,
        lat1: float,
        lon1: float,
        lat2: float,
        lon2: float,
    ) -> float:
        """
        Calculate haversine distance between two points.

        Args:
            lat1: First point latitude
            lon1: First point longitude
            lat2: Second point latitude
            lon2: Second point longitude

        Returns:
            float: Distance in kilometers
        """
        from math import radians, sin, cos, sqrt, atan2

        # Earth radius in kilometers
        R = 6371.0

        lat1_rad = radians(lat1)
        lon1_rad = radians(lon1)
        lat2_rad = radians(lat2)
        lon2_rad = radians(lon2)

        dlat = lat2_rad - lat1_rad
        dlon = lon2_rad - lon1_rad

        a = sin(dlat / 2) ** 2 + cos(lat1_rad) * cos(lat2_rad) * sin(dlon / 2) ** 2
        c = 2 * atan2(sqrt(a), sqrt(1 - a))

        distance = R * c
        return distance

    async def calculate_stop_coverage_score(
        self,
        route: Route,
        origin_lat: float,
        origin_lon: float,
        dest_lat: Optional[float],
        dest_lon: Optional[float],
    ) -> float:
        """
        Calculate how well a route's stops cover the rider's journey.

        Score based on:
        - Proximity of nearest stops to origin/destination
        - Number of intermediate stops (more stops = more flexibility)

        Args:
            route: Route to score
            origin_lat: Rider origin latitude
            origin_lon: Rider origin longitude
            dest_lat: Rider destination latitude (optional)
            dest_lon: Rider destination longitude (optional)

        Returns:
            float: Coverage score 0-1
        """
        is_valid, details = await self.validate_stop_sequence(
            route=route,
            origin_lat=origin_lat,
            origin_lon=origin_lon,
            dest_lat=dest_lat,
            dest_lon=dest_lon,
        )

        if not is_valid:
            return 0.0

        # Base score from proximity
        origin_distance = details.get("origin_stop_distance_km", 2.0)
        dest_distance = details.get("dest_stop_distance_km", 2.0)

        # Origin proximity score (closer = higher)
        # 0km = 1.0, 2km = 0.0
        origin_score = max(0.0, 1.0 - (origin_distance / 2.0))

        # Destination proximity score
        if dest_lat and dest_lon:
            dest_score = max(0.0, 1.0 - (dest_distance / 2.0))
            proximity_score = (origin_score + dest_score) / 2
        else:
            proximity_score = origin_score

        # Bonus for having intermediate stops (flexibility)
        if hasattr(route, "route_stops"):
            origin_idx = details.get("origin_stop_index", 0)
            dest_idx = details.get("dest_stop_index", len(route.route_stops) - 1)
            intermediate_stops = dest_idx - origin_idx - 1

            # Bonus: 0.1 per intermediate stop (max 0.2)
            flexibility_bonus = min(0.2, intermediate_stops * 0.1)
        else:
            flexibility_bonus = 0.0

        # Combine scores
        final_score = min(1.0, proximity_score + flexibility_bonus)

        return final_score

    async def validate_routes(
        self,
        routes: list[Route],
        origin_lat: float,
        origin_lon: float,
        dest_lat: Optional[float],
        dest_lon: Optional[float],
    ) -> list[Route]:
        """
        Validate multiple routes and filter out invalid ones.

        Args:
            routes: List of Route objects
            origin_lat: Rider origin latitude
            origin_lon: Rider origin longitude
            dest_lat: Rider destination latitude (optional)
            dest_lon: Rider destination longitude (optional)

        Returns:
            list[Route]: Valid routes only
        """
        valid_routes = []

        for route in routes:
            is_valid, details = await self.validate_stop_sequence(
                route=route,
                origin_lat=origin_lat,
                origin_lon=origin_lon,
                dest_lat=dest_lat,
                dest_lon=dest_lon,
            )

            if is_valid:
                valid_routes.append(route)
                logger.debug(
                    f"Route {route.id} valid: origin_idx={details['origin_stop_index']}, "
                    f"dest_idx={details['dest_stop_index']}"
                )
            else:
                logger.debug(f"Route {route.id} invalid stop sequence")

        return valid_routes

"""Composite scoring algorithm for route matching."""

import logging
from datetime import time
from decimal import Decimal

import numpy as np

from app.core.config import get_settings
from app.models.route import Route
from app.models.route_stop import RouteStop

settings = get_settings()
logger = logging.getLogger(__name__)


class RouteScorer:
    """Composite scoring engine for route matching."""

    def __init__(self) -> None:
        """Initialize scorer with weights from config."""
        self.weight_route_match = settings.weight_route_match
        self.weight_time_match = settings.weight_time_match
        self.weight_rating = settings.weight_rating
        self.weight_price = settings.weight_price

    def calculate_route_match_score(
        self,
        route: Route,
        origin_lat: float,
        origin_lon: float,
        dest_lat: float | None,
        dest_lon: float | None,
        proximity_threshold_meters: float = 500.0,
    ) -> tuple[float, bool, bool, bool]:
        """
        Calculate route match score based on stop coverage.

        Returns:
            tuple: (score, has_origin, has_destination, correct_direction)
        """
        from app.services.geospatial_utils import calculate_distance_haversine

        has_origin = False
        has_destination = False
        origin_stop_order = None
        dest_stop_order = None

        # Check which stops are near origin and destination
        for route_stop in route.route_stops:
            stop = route_stop.stop
            distance_to_origin = calculate_distance_haversine(
                stop.lat, stop.lon, origin_lat, origin_lon
            )

            if distance_to_origin <= proximity_threshold_meters:
                has_origin = True
                origin_stop_order = route_stop.stop_order

            if dest_lat is not None and dest_lon is not None:
                distance_to_dest = calculate_distance_haversine(
                    stop.lat, stop.lon, dest_lat, dest_lon
                )
                if distance_to_dest <= proximity_threshold_meters:
                    has_destination = True
                    dest_stop_order = route_stop.stop_order

        # Check directionality (destination must come after origin)
        correct_direction = True
        if has_origin and has_destination and origin_stop_order is not None and dest_stop_order is not None:
            correct_direction = dest_stop_order > origin_stop_order

        # Calculate score
        if has_origin and has_destination and correct_direction:
            score = 1.0  # Exact match
        elif has_origin or has_destination:
            score = 0.7  # Partial match
        else:
            score = 0.0  # No match

        return score, has_origin, has_destination, correct_direction

    def calculate_time_match_score(
        self,
        route_time: time,
        desired_time: time,
        max_window_minutes: int = 15,
    ) -> tuple[float, int]:
        """
        Calculate time match score based on departure time proximity.

        Returns:
            tuple: (score, time_difference_minutes)
        """
        # Convert to minutes since midnight
        route_minutes = route_time.hour * 60 + route_time.minute
        desired_minutes = desired_time.hour * 60 + desired_time.minute

        # Calculate difference (handle wrap-around)
        diff = abs(route_minutes - desired_minutes)
        if diff > 720:  # More than 12 hours, likely wrap-around
            diff = 1440 - diff

        # Calculate score
        if diff <= max_window_minutes:
            score = 1.0 - (diff / max_window_minutes)
        else:
            score = 0.0

        return score, diff

    def calculate_rating_score(self, driver_rating: float | None) -> float:
        """
        Calculate normalized rating score.

        Args:
            driver_rating: Driver rating (0-5 scale)

        Returns:
            float: Normalized score (0-1)
        """
        if driver_rating is None:
            return 0.5  # Neutral score for unrated drivers

        # Normalize 0-5 to 0-1
        return min(driver_rating / 5.0, 1.0)

    def calculate_price_score(
        self,
        route_price: Decimal,
        all_prices: list[Decimal],
    ) -> float:
        """
        Calculate price score (inverse - lower price is better).

        Args:
            route_price: Price for this route
            all_prices: Prices of all candidate routes

        Returns:
            float: Price score (0-1)
        """
        if not all_prices or len(all_prices) < 2:
            return 1.0  # Neutral if only one price

        prices_float = [float(p) for p in all_prices]
        min_price = min(prices_float)
        max_price = max(prices_float)

        if max_price == min_price:
            return 1.0

        # Inverse normalization (lower price = higher score)
        normalized = 1.0 - ((float(route_price) - min_price) / (max_price - min_price))
        return normalized

    def calculate_composite_score(
        self,
        route_match_score: float,
        time_match_score: float,
        rating_score: float,
        price_score: float,
    ) -> float:
        """
        Calculate weighted composite score.

        Args:
            route_match_score: Route match score (0-1)
            time_match_score: Time match score (0-1)
            rating_score: Rating score (0-1)
            price_score: Price score (0-1)

        Returns:
            float: Final composite score (0-1)
        """
        # Use NumPy for vectorized calculation
        scores = np.array(
            [route_match_score, time_match_score, rating_score, price_score], dtype=np.float64
        )
        weights = np.array(
            [
                self.weight_route_match,
                self.weight_time_match,
                self.weight_rating,
                self.weight_price,
            ],
            dtype=np.float64,
        )

        composite = float(np.dot(scores, weights))
        return composite

    def generate_explanation(
        self,
        route_match_score: float,
        has_origin: bool,
        has_destination: bool,
        correct_direction: bool,
        time_match_score: float,
        time_diff_minutes: int,
        driver_rating: float | None,
        price_score: float,
        route_price: Decimal,
    ) -> str:
        """
        Generate human-readable explanation for match score.

        Args:
            route_match_score: Route match score
            has_origin: Whether route covers origin
            has_destination: Whether route covers destination
            correct_direction: Whether direction is correct
            time_match_score: Time match score
            time_diff_minutes: Time difference in minutes
            driver_rating: Driver rating
            price_score: Price score
            route_price: Route price

        Returns:
            str: Human-readable explanation
        """
        parts = []

        # Route match explanation
        if route_match_score == 1.0:
            parts.append("✓ Exact match: Route covers both origin and destination")
        elif route_match_score == 0.7:
            if has_origin:
                parts.append("~ Partial match: Route covers your origin")
            elif has_destination:
                parts.append("~ Partial match: Route covers your destination")
        elif not correct_direction:
            parts.append("⚠ Route direction mismatch")

        # Time match explanation
        if time_match_score >= 0.8:
            parts.append(f"✓ Great timing: Departs in {time_diff_minutes} min")
        elif time_match_score >= 0.5:
            parts.append(f"~ Acceptable timing: Departs in {time_diff_minutes} min")

        # Rating explanation
        if driver_rating is not None and driver_rating >= 4.5:
            parts.append(f"✓ Highly rated driver: {driver_rating:.1f}/5.0 ⭐")
        elif driver_rating is not None and driver_rating >= 4.0:
            parts.append(f"✓ Good driver rating: {driver_rating:.1f}/5.0")

        # Price explanation
        if price_score >= 0.7:
            parts.append(f"✓ Good price: ₦{route_price:.2f}")
        elif price_score >= 0.4:
            parts.append(f"~ Fair price: ₦{route_price:.2f}")

        return " | ".join(parts) if parts else "Match found"

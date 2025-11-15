"""ML feature extraction service for route ranking."""

import logging
from datetime import datetime, time as time_type
from typing import Optional, Sequence
from uuid import UUID

import numpy as np

from app.core.config import get_settings
from app.models.route import Route
from app.schemas.matching import MatchRequest

settings = get_settings()
logger = logging.getLogger(__name__)


class FeatureExtractionService:
    """Extract ML-ready features for route ranking."""

    # Feature names in order (24 total)
    FEATURE_NAMES = [
        # Temporal (4)
        "hour_of_day",
        "day_of_week",
        "is_weekend",
        "is_rush_hour",
        # Match quality (3)
        "match_type_exact",
        "time_diff_minutes",
        "time_diff_normalized",
        # Pricing (3)
        "price_per_seat",
        "price_rank",
        "price_percentile",
        # Route characteristics (3)
        "route_length",
        "destination_position",
        "destination_position_normalized",
        # Driver features (4)
        "driver_rating",
        "driver_rating_count",
        "driver_cancellation_rate",
        "driver_completed_trips",
        # Availability (2)
        "seats_available",
        "seats_utilization",
        # Distance features (3)
        "origin_distance_km",
        "dest_distance_km",
        "total_distance_km",
        # Hub features (2)
        "has_origin_hub",
        "has_dest_hub",
    ]

    def __init__(self):
        """Initialize feature extraction service."""
        self.feature_count = len(self.FEATURE_NAMES)

    def extract_features(
        self,
        route: Route,
        request: MatchRequest,
        all_prices: list[float],
        driver_stats: Optional[dict] = None,
        match_type: str = "PARTIAL",
        origin_distance_km: float = 0.0,
        dest_distance_km: float = 0.0,
    ) -> np.ndarray:
        """
        Extract feature vector for a single route.

        Args:
            route: Route object
            request: Match request
            all_prices: All candidate prices for ranking
            driver_stats: Driver statistics (from materialized view)
            match_type: EXACT or PARTIAL hub match
            origin_distance_km: Distance from origin to route
            dest_distance_km: Distance from dest to route

        Returns:
            np.ndarray: Feature vector (24 features)
        """
        features = []

        # === Temporal features (4) ===
        desired_dt = datetime.combine(datetime.today(), request.desired_time)
        hour = desired_dt.hour
        weekday = desired_dt.weekday()

        features.extend(
            [
                hour,  # 0-23
                weekday,  # 0-6 (Monday=0)
                1.0 if weekday >= 5 else 0.0,  # Weekend flag
                1.0
                if (7 <= hour <= 9) or (17 <= hour <= 19)
                else 0.0,  # Rush hour
            ]
        )

        # === Match quality (3) ===
        features.append(1.0 if match_type == "EXACT" else 0.0)

        # Time difference in minutes
        route_minutes = route.departure_time.hour * 60 + route.departure_time.minute
        desired_minutes = request.desired_time.hour * 60 + request.desired_time.minute
        time_diff = abs(route_minutes - desired_minutes)

        # Handle midnight wrap-around
        if time_diff > 720:  # More than 12 hours
            time_diff = 1440 - time_diff

        features.extend(
            [
                float(time_diff),
                time_diff / 60.0,  # Normalized to hours
            ]
        )

        # === Pricing features (3) ===
        price = float(route.base_price)

        # Price rank (1 = cheapest)
        sorted_prices = sorted(all_prices)
        price_rank = sorted_prices.index(price) + 1 if price in sorted_prices else len(sorted_prices)

        # Price percentile (0 = cheapest, 1 = most expensive)
        if len(all_prices) > 1 and max(all_prices) != min(all_prices):
            price_percentile = (price - min(all_prices)) / (max(all_prices) - min(all_prices))
        else:
            price_percentile = 0.5

        features.extend(
            [
                price,
                float(price_rank),
                price_percentile,
            ]
        )

        # === Route characteristics (3) ===
        route_length = len(route.route_stops) if hasattr(route, "route_stops") and route.route_stops else 0

        # Estimate destination position (middle of route if unknown)
        dest_position = route_length // 2
        dest_position_normalized = 0.5 if route_length > 0 else 0.0

        # If we have route stops loaded, calculate actual position
        if route_length > 1:
            dest_position_normalized = dest_position / route_length

        features.extend(
            [
                float(route_length),
                float(dest_position),
                dest_position_normalized,
            ]
        )

        # === Driver features (4) ===
        if driver_stats:
            driver_rating = float(driver_stats.get("rating_avg", 0.0))
            driver_rating_count = int(driver_stats.get("rating_count", 0))
            driver_cancellation_rate = float(driver_stats.get("cancellation_rate", 0.0))
            driver_completed_trips = int(driver_stats.get("completed_trips", 0))
        else:
            # Default neutral values
            driver_rating = 4.0
            driver_rating_count = 0
            driver_cancellation_rate = 0.0
            driver_completed_trips = 0

        features.extend(
            [
                driver_rating,
                float(driver_rating_count),
                driver_cancellation_rate,
                float(driver_completed_trips),
            ]
        )

        # === Availability features (2) ===
        seats_available = route.seats_available
        seats_total = route.seats_total
        seats_utilization = seats_available / seats_total if seats_total > 0 else 0.0

        features.extend(
            [
                float(seats_available),
                seats_utilization,
            ]
        )

        # === Distance features (3) ===
        total_distance = origin_distance_km + dest_distance_km

        features.extend(
            [
                origin_distance_km,
                dest_distance_km,
                total_distance,
            ]
        )

        # === Hub features (2) ===
        has_origin_hub = 1.0 if route.origin_hub_id is not None else 0.0
        has_dest_hub = 1.0 if route.destination_hub_id is not None else 0.0

        features.extend(
            [
                has_origin_hub,
                has_dest_hub,
            ]
        )

        # Validate feature count
        feature_array = np.array(features, dtype=np.float32)
        if len(feature_array) != self.feature_count:
            logger.error(
                f"Feature count mismatch: expected {self.feature_count}, got {len(feature_array)}"
            )

        return feature_array

    def extract_batch_features(
        self,
        routes: Sequence[Route],
        request: MatchRequest,
        driver_stats_map: dict[str, dict],
        match_types: dict[str, str],
        distances: dict[UUID, dict],
    ) -> np.ndarray:
        """
        Extract features for all candidate routes.

        Args:
            routes: Sequence of Route objects
            request: Match request
            driver_stats_map: Map of driver_id -> stats dict
            match_types: Map of route_id -> match type (EXACT/PARTIAL)
            distances: Map of route_id -> {origin_km, dest_km}

        Returns:
            np.ndarray: Feature matrix (n_routes x 24)
        """
        all_prices = [float(r.base_price) for r in routes]
        feature_matrix = []

        for route in routes:
            driver_stats = driver_stats_map.get(str(route.driver_id))
            match_type = match_types.get(str(route.id), "PARTIAL")

            # Get distances
            route_distances = distances.get(route.id, {})
            origin_distance = route_distances.get("origin_km", 0.0)
            dest_distance = route_distances.get("dest_km", 0.0)

            features = self.extract_features(
                route=route,
                request=request,
                all_prices=all_prices,
                driver_stats=driver_stats,
                match_type=match_type,
                origin_distance_km=origin_distance,
                dest_distance_km=dest_distance,
            )

            feature_matrix.append(features)

        return np.array(feature_matrix)

    def get_feature_names(self) -> list[str]:
        """
        Get list of feature names.

        Returns:
            list[str]: Feature names in order
        """
        return self.FEATURE_NAMES.copy()

    def get_feature_importance_weights(self) -> dict[str, float]:
        """
        Get default feature importance weights for scoring.

        These weights are used for ML-based scoring before
        actual model training.

        Returns:
            dict[str, float]: Feature name -> importance weight
        """
        return {
            # Temporal - moderate importance
            "hour_of_day": 0.02,
            "day_of_week": 0.01,
            "is_weekend": 0.02,
            "is_rush_hour": 0.03,
            # Match quality - high importance
            "match_type_exact": 0.15,
            "time_diff_minutes": -0.008,  # Negative: lower diff = better
            "time_diff_normalized": -0.10,
            # Pricing - moderate importance
            "price_per_seat": -0.001,  # Negative: lower price = better
            "price_rank": -0.05,
            "price_percentile": -0.08,
            # Route characteristics - low importance
            "route_length": 0.01,
            "destination_position": 0.0,
            "destination_position_normalized": 0.02,
            # Driver features - high importance
            "driver_rating": 0.12,
            "driver_rating_count": 0.03,
            "driver_cancellation_rate": -0.15,  # Negative: lower = better
            "driver_completed_trips": 0.05,
            # Availability - moderate importance
            "seats_available": 0.04,
            "seats_utilization": 0.03,
            # Distance - high importance
            "origin_distance_km": -0.08,  # Negative: closer = better
            "dest_distance_km": -0.06,
            "total_distance_km": -0.05,
            # Hub features - moderate importance
            "has_origin_hub": 0.06,
            "has_dest_hub": 0.04,
        }

    def calculate_ml_score(self, features: np.ndarray) -> float:
        """
        Calculate ML-based score using feature weights.

        This is a simple weighted sum approach before actual
        ML model training.

        Args:
            features: Feature vector (24 features)

        Returns:
            float: ML score (unbounded, higher = better)
        """
        weights = self.get_feature_importance_weights()
        weight_array = np.array([weights[name] for name in self.FEATURE_NAMES])

        # Weighted sum
        raw_score = np.dot(features, weight_array)

        # Add base score to avoid negatives
        base_score = 0.5
        final_score = base_score + raw_score

        # Clamp to 0-1 range
        return max(0.0, min(1.0, final_score))

    def explain_ml_score(self, features: np.ndarray) -> dict:
        """
        Explain ML score by showing feature contributions.

        Args:
            features: Feature vector

        Returns:
            dict: Feature contributions and metadata
        """
        weights = self.get_feature_importance_weights()
        contributions = {}

        for i, feature_name in enumerate(self.FEATURE_NAMES):
            feature_value = features[i]
            weight = weights[feature_name]
            contribution = feature_value * weight

            contributions[feature_name] = {
                "value": float(feature_value),
                "weight": weight,
                "contribution": float(contribution),
            }

        # Sort by absolute contribution
        sorted_contributions = sorted(
            contributions.items(),
            key=lambda x: abs(x[1]["contribution"]),
            reverse=True,
        )

        return {
            "total_score": self.calculate_ml_score(features),
            "contributions": dict(sorted_contributions[:10]),  # Top 10
            "feature_count": len(features),
        }

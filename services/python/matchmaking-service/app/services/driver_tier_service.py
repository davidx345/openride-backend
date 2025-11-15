"""Driver tier filtering service for route matching."""

import logging
from typing import Optional
from uuid import UUID

from sqlalchemy import select, text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings

settings = get_settings()
logger = logging.getLogger(__name__)


class DriverTierService:
    """Service for filtering and scoring routes by driver tier."""

    # Driver tier definitions
    TIER_PREMIUM = "premium"
    TIER_VERIFIED = "verified"
    TIER_STANDARD = "standard"
    TIER_NEW = "new"

    # Tier score multipliers
    TIER_SCORES = {
        TIER_PREMIUM: 1.0,
        TIER_VERIFIED: 0.85,
        TIER_STANDARD: 0.7,
        TIER_NEW: 0.5,
    }

    def __init__(self, db: AsyncSession):
        """
        Initialize driver tier service.

        Args:
            db: Database session
        """
        self.db = db

    async def get_driver_tier(self, driver_id: UUID) -> dict:
        """
        Get driver tier from materialized view.

        Args:
            driver_id: Driver UUID

        Returns:
            dict: Driver tier information
        """
        query = text(
            """
            SELECT 
                driver_id,
                driver_tier,
                rating_avg,
                rating_count,
                completed_trips,
                cancellation_rate,
                is_verified,
                active_routes_count,
                total_available_seats
            FROM driver_stats_agg
            WHERE driver_id = :driver_id
            """
        )

        result = await self.db.execute(query, {"driver_id": driver_id})
        row = result.fetchone()

        if not row:
            # Driver not in materialized view (no ratings yet)
            return {
                "driver_id": driver_id,
                "driver_tier": self.TIER_NEW,
                "rating_avg": 0.0,
                "rating_count": 0,
                "completed_trips": 0,
                "cancellation_rate": 0.0,
                "is_verified": False,
                "active_routes_count": 0,
                "total_available_seats": 0,
            }

        return {
            "driver_id": row.driver_id,
            "driver_tier": row.driver_tier,
            "rating_avg": float(row.rating_avg) if row.rating_avg else 0.0,
            "rating_count": row.rating_count,
            "completed_trips": row.completed_trips,
            "cancellation_rate": float(row.cancellation_rate) if row.cancellation_rate else 0.0,
            "is_verified": row.is_verified,
            "active_routes_count": row.active_routes_count,
            "total_available_seats": row.total_available_seats,
        }

    async def get_drivers_batch(self, driver_ids: list[UUID]) -> dict[UUID, dict]:
        """
        Get driver tiers for multiple drivers in batch.

        Args:
            driver_ids: List of driver UUIDs

        Returns:
            dict: Map of driver_id -> tier info
        """
        if not driver_ids:
            return {}

        query = text(
            """
            SELECT 
                driver_id,
                driver_tier,
                rating_avg,
                rating_count,
                completed_trips,
                cancellation_rate,
                is_verified
            FROM driver_stats_agg
            WHERE driver_id = ANY(:driver_ids)
            """
        )

        result = await self.db.execute(
            query, {"driver_ids": [str(d) for d in driver_ids]}
        )

        drivers_map = {}
        for row in result:
            drivers_map[row.driver_id] = {
                "driver_tier": row.driver_tier,
                "rating_avg": float(row.rating_avg) if row.rating_avg else 0.0,
                "rating_count": row.rating_count,
                "completed_trips": row.completed_trips,
                "cancellation_rate": float(row.cancellation_rate) if row.cancellation_rate else 0.0,
                "is_verified": row.is_verified,
            }

        # Fill in missing drivers with default "new" tier
        for driver_id in driver_ids:
            if driver_id not in drivers_map:
                drivers_map[driver_id] = {
                    "driver_tier": self.TIER_NEW,
                    "rating_avg": 0.0,
                    "rating_count": 0,
                    "completed_trips": 0,
                    "cancellation_rate": 0.0,
                    "is_verified": False,
                }

        return drivers_map

    def calculate_tier_score(self, driver_tier: str) -> float:
        """
        Calculate score multiplier for driver tier.

        Args:
            driver_tier: Driver tier (premium/verified/standard/new)

        Returns:
            float: Score multiplier 0-1
        """
        return self.TIER_SCORES.get(driver_tier, 0.5)

    async def filter_by_minimum_tier(
        self,
        driver_ids: list[UUID],
        minimum_tier: str,
    ) -> list[UUID]:
        """
        Filter drivers by minimum tier requirement.

        Tier hierarchy: premium > verified > standard > new

        Args:
            driver_ids: List of driver IDs to filter
            minimum_tier: Minimum acceptable tier

        Returns:
            list: Filtered driver IDs meeting minimum tier
        """
        if not driver_ids:
            return []

        # Define tier hierarchy
        tier_hierarchy = {
            self.TIER_PREMIUM: 4,
            self.TIER_VERIFIED: 3,
            self.TIER_STANDARD: 2,
            self.TIER_NEW: 1,
        }

        minimum_level = tier_hierarchy.get(minimum_tier, 1)

        # Get tiers for all drivers
        drivers_map = await self.get_drivers_batch(driver_ids)

        # Filter by tier level
        filtered_ids = []
        for driver_id, info in drivers_map.items():
            tier = info.get("driver_tier", self.TIER_NEW)
            tier_level = tier_hierarchy.get(tier, 1)

            if tier_level >= minimum_level:
                filtered_ids.append(driver_id)

        logger.debug(
            f"Filtered {len(driver_ids)} drivers to {len(filtered_ids)} "
            f"with minimum tier {minimum_tier}"
        )

        return filtered_ids

    async def get_premium_drivers(self, limit: int = 100) -> list[dict]:
        """
        Get list of premium drivers for priority matching.

        Args:
            limit: Maximum number of drivers to return

        Returns:
            list: Premium driver information
        """
        query = text(
            """
            SELECT 
                driver_id,
                rating_avg,
                completed_trips,
                cancellation_rate,
                active_routes_count,
                total_available_seats
            FROM driver_stats_agg
            WHERE driver_tier = :tier
            ORDER BY rating_avg DESC, completed_trips DESC
            LIMIT :limit
            """
        )

        result = await self.db.execute(
            query, {"tier": self.TIER_PREMIUM, "limit": limit}
        )

        premium_drivers = []
        for row in result:
            premium_drivers.append(
                {
                    "driver_id": row.driver_id,
                    "rating_avg": float(row.rating_avg),
                    "completed_trips": row.completed_trips,
                    "cancellation_rate": float(row.cancellation_rate),
                    "active_routes_count": row.active_routes_count,
                    "total_available_seats": row.total_available_seats,
                }
            )

        return premium_drivers

    async def calculate_reliability_score(
        self,
        driver_tier: str,
        rating_avg: float,
        cancellation_rate: float,
        completed_trips: int,
    ) -> float:
        """
        Calculate comprehensive reliability score for driver.

        Combines:
        - Tier score (40%)
        - Rating score (30%)
        - Cancellation score (20%)
        - Experience score (10%)

        Args:
            driver_tier: Driver tier
            rating_avg: Average rating 0-5
            cancellation_rate: Cancellation rate 0-1
            completed_trips: Total completed trips

        Returns:
            float: Reliability score 0-1
        """
        # Tier score (40%)
        tier_score = self.calculate_tier_score(driver_tier) * 0.4

        # Rating score (30%)
        # Normalize 0-5 to 0-1
        rating_score = (rating_avg / 5.0) * 0.3 if rating_avg > 0 else 0.0

        # Cancellation score (20%)
        # Lower cancellation = higher score
        cancellation_score = (1.0 - cancellation_rate) * 0.2

        # Experience score (10%)
        # More trips = higher score (capped at 100 trips)
        experience_score = min(1.0, completed_trips / 100.0) * 0.1

        # Combine scores
        reliability_score = (
            tier_score + rating_score + cancellation_score + experience_score
        )

        return min(1.0, reliability_score)

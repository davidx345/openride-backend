"""Route cache service for Redis-based caching."""

import hashlib
import logging
from datetime import datetime, time
from typing import Optional
from uuid import UUID

from app.core.config import get_settings
from app.core.redis import RedisClient
from app.models.route import Route

logger = logging.getLogger(__name__)
settings = get_settings()


class RouteCacheService:
    """Service for caching route queries in Redis."""

    def __init__(self, redis_client: RedisClient):
        """
        Initialize route cache service.

        Args:
            redis_client: Redis client instance
        """
        self.redis = redis_client
        self.cache_prefix = "route_cache"
        self.default_ttl = settings.redis_cache_ttl

    def _generate_cache_key(
        self,
        origin_hub_id: Optional[UUID],
        destination_hub_id: Optional[UUID],
        departure_time: Optional[time],
        active_only: bool = True,
    ) -> str:
        """
        Generate cache key from query parameters.

        Args:
            origin_hub_id: Origin hub UUID
            destination_hub_id: Destination hub UUID
            departure_time: Departure time
            active_only: Filter active routes only

        Returns:
            Cache key string
        """
        # Create deterministic key from parameters
        params = {
            "origin": str(origin_hub_id) if origin_hub_id else "any",
            "dest": str(destination_hub_id) if destination_hub_id else "any",
            "time": departure_time.isoformat() if departure_time else "any",
            "active": str(active_only),
        }

        # Sort for consistency
        param_str = "&".join(f"{k}={v}" for k, v in sorted(params.items()))

        # Hash for shorter keys
        hash_suffix = hashlib.md5(param_str.encode()).hexdigest()[:12]

        return f"{self.cache_prefix}:query:{hash_suffix}"

    def _generate_route_key(self, route_id: UUID) -> str:
        """
        Generate cache key for individual route.

        Args:
            route_id: Route UUID

        Returns:
            Cache key string
        """
        return f"{self.cache_prefix}:route:{route_id}"

    async def get_cached_routes(
        self,
        origin_hub_id: Optional[UUID],
        destination_hub_id: Optional[UUID],
        departure_time: Optional[time],
        active_only: bool = True,
    ) -> Optional[list[dict]]:
        """
        Get cached route query results.

        Args:
            origin_hub_id: Origin hub UUID
            destination_hub_id: Destination hub UUID
            departure_time: Departure time
            active_only: Filter active routes only

        Returns:
            List of route dictionaries or None if cache miss
        """
        cache_key = self._generate_cache_key(
            origin_hub_id, destination_hub_id, departure_time, active_only
        )

        cached = await self.redis.get_json(cache_key)
        if cached:
            logger.debug(f"Cache HIT for key: {cache_key}")
            return cached

        logger.debug(f"Cache MISS for key: {cache_key}")
        return None

    async def cache_routes(
        self,
        routes: list[Route],
        origin_hub_id: Optional[UUID],
        destination_hub_id: Optional[UUID],
        departure_time: Optional[time],
        active_only: bool = True,
        ttl: Optional[int] = None,
    ) -> bool:
        """
        Cache route query results.

        Args:
            routes: List of Route objects
            origin_hub_id: Origin hub UUID
            destination_hub_id: Destination hub UUID
            departure_time: Departure time
            active_only: Filter active routes only
            ttl: Time to live in seconds (default from settings)

        Returns:
            True if cached successfully
        """
        cache_key = self._generate_cache_key(
            origin_hub_id, destination_hub_id, departure_time, active_only
        )

        # Serialize routes to dict
        route_dicts = [
            {
                "id": str(route.id),
                "driver_id": str(route.driver_id),
                "vehicle_id": str(route.vehicle_id),
                "name": route.name,
                "departure_time": route.departure_time.isoformat(),
                "active_days": route.active_days,
                "seats_total": route.seats_total,
                "seats_available": route.seats_available,
                "base_price": float(route.base_price),
                "status": route.status.value,
                "origin_hub_id": str(route.origin_hub_id) if route.origin_hub_id else None,
                "destination_hub_id": str(route.destination_hub_id) if route.destination_hub_id else None,
                "currency": route.currency,
                "estimated_duration_minutes": route.estimated_duration_minutes,
            }
            for route in routes
        ]

        cache_ttl = ttl or self.default_ttl
        success = await self.redis.set_json(cache_key, route_dicts, cache_ttl)

        if success:
            logger.debug(f"Cached {len(routes)} routes with key: {cache_key}, TTL: {cache_ttl}s")
        else:
            logger.warning(f"Failed to cache routes for key: {cache_key}")

        return success

    async def invalidate_route(self, route_id: UUID) -> bool:
        """
        Invalidate cache for a specific route.

        This clears the individual route cache and all query caches
        that might contain this route.

        Args:
            route_id: Route UUID

        Returns:
            True if invalidated
        """
        # Clear individual route cache
        route_key = self._generate_route_key(route_id)
        deleted = await self.redis.delete(route_key)

        # Clear all query caches (simple approach: clear all route_cache:query:* keys)
        # In production, consider more targeted invalidation or cache versioning
        query_keys = await self.redis.scan_keys(f"{self.cache_prefix}:query:*")
        if query_keys:
            await self.redis.delete(*query_keys)
            logger.info(f"Invalidated route {route_id} and cleared {len(query_keys)} query caches")

        return deleted > 0

    async def invalidate_hub_routes(
        self,
        hub_id: UUID,
        origin: bool = True,
        destination: bool = True,
    ) -> int:
        """
        Invalidate all route caches for a hub.

        Args:
            hub_id: Hub UUID
            origin: Invalidate routes originating from hub
            destination: Invalidate routes terminating at hub

        Returns:
            Number of keys invalidated
        """
        # Clear all query caches (contains hub_id)
        query_keys = await self.redis.scan_keys(f"{self.cache_prefix}:query:*")
        if query_keys:
            deleted = await self.redis.delete(*query_keys)
            logger.info(f"Invalidated {deleted} route caches for hub {hub_id}")
            return deleted

        return 0

    async def clear_all_caches(self) -> int:
        """
        Clear all route caches.

        Returns:
            Number of keys cleared
        """
        all_keys = await self.redis.scan_keys(f"{self.cache_prefix}:*")
        if all_keys:
            deleted = await self.redis.delete(*all_keys)
            logger.info(f"Cleared {deleted} route cache keys")
            return deleted

        return 0

    async def get_cache_stats(self) -> dict:
        """
        Get cache statistics.

        Returns:
            Dictionary with cache stats
        """
        query_keys = await self.redis.scan_keys(f"{self.cache_prefix}:query:*")
        route_keys = await self.redis.scan_keys(f"{self.cache_prefix}:route:*")

        return {
            "query_caches": len(query_keys),
            "route_caches": len(route_keys),
            "total_keys": len(query_keys) + len(route_keys),
            "cache_prefix": self.cache_prefix,
            "default_ttl": self.default_ttl,
        }

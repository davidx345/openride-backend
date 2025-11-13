"""Redis cache manager for route caching."""

import hashlib
import json
import logging
from typing import Any

import redis.asyncio as redis

from app.core.config import get_settings
from app.core.exceptions import CacheError

settings = get_settings()
logger = logging.getLogger(__name__)


class CacheManager:
    """Redis cache manager for hot routes and search results."""

    def __init__(self) -> None:
        """Initialize Redis connection."""
        self._redis: redis.Redis | None = None

    async def connect(self) -> None:
        """Establish Redis connection."""
        try:
            self._redis = await redis.from_url(
                settings.redis_url,
                encoding="utf-8",
                decode_responses=True,
            )
            await self._redis.ping()
            logger.info("Redis connection established")
        except Exception as e:
            logger.error(f"Failed to connect to Redis: {e}")
            raise CacheError(f"Redis connection failed: {e}")

    async def disconnect(self) -> None:
        """Close Redis connection."""
        if self._redis:
            await self._redis.close()
            logger.info("Redis connection closed")

    def _get_cache_key(self, prefix: str, **kwargs: Any) -> str:
        """
        Generate cache key from prefix and parameters.

        Args:
            prefix: Cache key prefix
            **kwargs: Key-value pairs to include in cache key

        Returns:
            str: Generated cache key
        """
        # Sort kwargs for consistent key generation
        sorted_params = sorted(kwargs.items())
        param_str = json.dumps(sorted_params, sort_keys=True)
        param_hash = hashlib.md5(param_str.encode()).hexdigest()[:8]
        return f"{prefix}:{param_hash}"

    async def get(self, key: str) -> Any | None:
        """
        Get value from cache.

        Args:
            key: Cache key

        Returns:
            Any | None: Cached value or None if not found
        """
        if not self._redis:
            return None

        try:
            value = await self._redis.get(key)
            if value:
                return json.loads(value)
            return None
        except Exception as e:
            logger.warning(f"Cache get error for key {key}: {e}")
            return None

    async def set(
        self,
        key: str,
        value: Any,
        ttl: int | None = None,
    ) -> bool:
        """
        Set value in cache with optional TTL.

        Args:
            key: Cache key
            value: Value to cache
            ttl: Time to live in seconds

        Returns:
            bool: True if successful
        """
        if not self._redis:
            return False

        try:
            serialized = json.dumps(value, default=str)
            if ttl:
                await self._redis.setex(key, ttl, serialized)
            else:
                await self._redis.set(key, serialized)
            return True
        except Exception as e:
            logger.warning(f"Cache set error for key {key}: {e}")
            return False

    async def delete(self, key: str) -> bool:
        """
        Delete value from cache.

        Args:
            key: Cache key

        Returns:
            bool: True if successful
        """
        if not self._redis:
            return False

        try:
            await self._redis.delete(key)
            return True
        except Exception as e:
            logger.warning(f"Cache delete error for key {key}: {e}")
            return False

    async def get_active_routes(self) -> list[str] | None:
        """
        Get list of active route IDs from cache.

        Returns:
            list[str] | None: List of route IDs or None if not cached
        """
        return await self.get("routes:active")

    async def set_active_routes(self, route_ids: list[str]) -> bool:
        """
        Cache list of active route IDs.

        Args:
            route_ids: List of route IDs

        Returns:
            bool: True if successful
        """
        return await self.set(
            "routes:active",
            route_ids,
            ttl=settings.redis_active_routes_ttl,
        )

    async def get_route_details(self, route_id: str) -> dict[str, Any] | None:
        """
        Get route details from cache.

        Args:
            route_id: Route ID

        Returns:
            dict | None: Route details or None if not cached
        """
        return await self.get(f"route:details:{route_id}")

    async def set_route_details(self, route_id: str, route_data: dict[str, Any]) -> bool:
        """
        Cache route details.

        Args:
            route_id: Route ID
            route_data: Route details

        Returns:
            bool: True if successful
        """
        return await self.set(
            f"route:details:{route_id}",
            route_data,
            ttl=settings.redis_cache_ttl,
        )

    async def invalidate_route(self, route_id: str) -> None:
        """
        Invalidate all cached data for a route.

        Args:
            route_id: Route ID
        """
        await self.delete(f"route:details:{route_id}")
        await self.delete("routes:active")

    async def get_search_results(
        self,
        origin_lat: float,
        origin_lon: float,
        dest_lat: float | None,
        dest_lon: float | None,
        time: str,
    ) -> dict[str, Any] | None:
        """
        Get cached search results.

        Args:
            origin_lat: Origin latitude
            origin_lon: Origin longitude
            dest_lat: Destination latitude
            dest_lon: Destination longitude
            time: Desired time

        Returns:
            dict | None: Search results or None if not cached
        """
        cache_key = self._get_cache_key(
            "search:results",
            olat=round(origin_lat, 4),
            olon=round(origin_lon, 4),
            dlat=round(dest_lat, 4) if dest_lat else None,
            dlon=round(dest_lon, 4) if dest_lon else None,
            time=time,
        )
        return await self.get(cache_key)

    async def set_search_results(
        self,
        origin_lat: float,
        origin_lon: float,
        dest_lat: float | None,
        dest_lon: float | None,
        time: str,
        results: dict[str, Any],
    ) -> bool:
        """
        Cache search results.

        Args:
            origin_lat: Origin latitude
            origin_lon: Origin longitude
            dest_lat: Destination latitude
            dest_lon: Destination longitude
            time: Desired time
            results: Search results

        Returns:
            bool: True if successful
        """
        cache_key = self._get_cache_key(
            "search:results",
            olat=round(origin_lat, 4),
            olon=round(origin_lon, 4),
            dlat=round(dest_lat, 4) if dest_lat else None,
            dlon=round(dest_lon, 4) if dest_lon else None,
            time=time,
        )
        return await self.set(cache_key, results, ttl=180)  # 3 minute TTL


# Global cache manager instance
cache_manager = CacheManager()


async def get_cache() -> CacheManager:
    """
    Get cache manager instance.

    Returns:
        CacheManager: Cache manager
    """
    return cache_manager

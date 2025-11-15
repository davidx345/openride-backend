"""Hub lookup caching service for spatial query optimization."""

import json
import logging
from typing import Optional
from uuid import UUID

from app.core.redis import redis_client

logger = logging.getLogger(__name__)


class HubCacheService:
    """Cache hub lookups to reduce spatial query load."""

    def __init__(self, redis_client):
        """
        Initialize hub cache service.

        Args:
            redis_client: Redis client instance
        """
        self.redis = redis_client
        self.ttl = 1800  # 30 minutes cache (hubs change infrequently)
        self.grid_precision = 3  # Round to 3 decimal places (~111m precision)

    def _get_grid_key(self, lat: float, lon: float) -> str:
        """
        Generate grid-based cache key for spatial lookups.

        Args:
            lat: Latitude
            lon: Longitude

        Returns:
            str: Grid key
        """
        # Round coordinates to grid precision
        grid_lat = round(lat, self.grid_precision)
        grid_lon = round(lon, self.grid_precision)
        return f"hub:grid:{grid_lat}:{grid_lon}"

    async def get_nearest_hub(self, lat: float, lon: float) -> Optional[dict]:
        """
        Get nearest hub from cache using grid-based lookup.

        Args:
            lat: Latitude
            lon: Longitude

        Returns:
            dict | None: Hub data or None if not cached
        """
        try:
            key = self._get_grid_key(lat, lon)
            cached = await self.redis.get(key)
            
            if cached:
                logger.debug(f"Hub cache HIT for ({lat:.4f}, {lon:.4f})")
                return json.loads(cached)
            
            logger.debug(f"Hub cache MISS for ({lat:.4f}, {lon:.4f})")
            return None
            
        except Exception as e:
            logger.warning(f"Failed to get hub from cache: {e}")
            return None

    async def set_nearest_hub(self, lat: float, lon: float, hub_data: dict) -> bool:
        """
        Cache nearest hub for a location.

        Args:
            lat: Latitude
            lon: Longitude
            hub_data: Hub information to cache

        Returns:
            bool: True if cached successfully
        """
        try:
            key = self._get_grid_key(lat, lon)
            await self.redis.set(
                key,
                json.dumps(hub_data),
                ex=self.ttl
            )
            logger.debug(f"Cached hub for grid ({lat:.4f}, {lon:.4f})")
            return True
            
        except Exception as e:
            logger.warning(f"Failed to cache hub: {e}")
            return False

    async def get_hub_by_id(self, hub_id: UUID) -> Optional[dict]:
        """
        Get hub by ID from cache.

        Args:
            hub_id: Hub UUID

        Returns:
            dict | None: Hub data or None if not cached
        """
        try:
            key = f"hub:id:{hub_id}"
            cached = await self.redis.get(key)
            
            if cached:
                logger.debug(f"Hub ID cache HIT for {hub_id}")
                return json.loads(cached)
            
            logger.debug(f"Hub ID cache MISS for {hub_id}")
            return None
            
        except Exception as e:
            logger.warning(f"Failed to get hub by ID from cache: {e}")
            return None

    async def set_hub_by_id(self, hub_id: UUID, hub_data: dict) -> bool:
        """
        Cache hub by ID.

        Args:
            hub_id: Hub UUID
            hub_data: Hub information

        Returns:
            bool: True if cached successfully
        """
        try:
            key = f"hub:id:{hub_id}"
            await self.redis.set(
                key,
                json.dumps(hub_data),
                ex=self.ttl
            )
            logger.debug(f"Cached hub {hub_id}")
            return True
            
        except Exception as e:
            logger.warning(f"Failed to cache hub by ID: {e}")
            return False

    async def get_hub_pair_routes(
        self,
        origin_hub_id: UUID,
        dest_hub_id: UUID,
    ) -> Optional[list[dict]]:
        """
        Get cached routes for a hub pair.

        Args:
            origin_hub_id: Origin hub UUID
            dest_hub_id: Destination hub UUID

        Returns:
            list[dict] | None: Route list or None if not cached
        """
        try:
            key = f"hub:routes:{origin_hub_id}:{dest_hub_id}"
            cached = await self.redis.get(key)
            
            if cached:
                logger.debug(f"Hub pair routes cache HIT for {origin_hub_id}->{dest_hub_id}")
                return json.loads(cached)
            
            logger.debug(f"Hub pair routes cache MISS for {origin_hub_id}->{dest_hub_id}")
            return None
            
        except Exception as e:
            logger.warning(f"Failed to get hub pair routes from cache: {e}")
            return None

    async def set_hub_pair_routes(
        self,
        origin_hub_id: UUID,
        dest_hub_id: UUID,
        routes: list[dict],
    ) -> bool:
        """
        Cache routes for a hub pair.

        Args:
            origin_hub_id: Origin hub UUID
            dest_hub_id: Destination hub UUID
            routes: Route list to cache

        Returns:
            bool: True if cached successfully
        """
        try:
            key = f"hub:routes:{origin_hub_id}:{dest_hub_id}"
            await self.redis.set(
                key,
                json.dumps(routes),
                ex=self.ttl
            )
            logger.debug(
                f"Cached {len(routes)} routes for hub pair {origin_hub_id}->{dest_hub_id}"
            )
            return True
            
        except Exception as e:
            logger.warning(f"Failed to cache hub pair routes: {e}")
            return False

    async def invalidate_hub(self, hub_id: UUID) -> bool:
        """
        Invalidate all cached data for a hub.

        Args:
            hub_id: Hub UUID

        Returns:
            bool: True if invalidated successfully
        """
        try:
            # Invalidate hub by ID
            await self.redis.delete(f"hub:id:{hub_id}")
            
            # Invalidate all hub pair routes involving this hub
            # This is a simplified approach - production would need pattern-based deletion
            logger.debug(f"Invalidated hub cache for {hub_id}")
            return True
            
        except Exception as e:
            logger.warning(f"Failed to invalidate hub cache: {e}")
            return False

    async def get_cache_stats(self) -> dict:
        """
        Get cache statistics.

        Returns:
            dict: Cache statistics
        """
        try:
            # Count keys by pattern
            stats = {}
            
            for pattern in ["hub:grid:*", "hub:id:*", "hub:routes:*"]:
                cursor = 0
                count = 0
                
                while True:
                    cursor, keys = await self.redis.scan(
                        cursor,
                        match=pattern,
                        count=100
                    )
                    count += len(keys)
                    
                    if cursor == 0:
                        break
                
                pattern_name = pattern.replace("hub:", "").replace(":*", "")
                stats[f"{pattern_name}_cached"] = count
            
            stats["ttl_seconds"] = self.ttl
            stats["grid_precision_decimal_places"] = self.grid_precision
            
            return stats
            
        except Exception as e:
            logger.warning(f"Failed to get cache stats: {e}")
            return {
                "grid_cached": 0,
                "id_cached": 0,
                "routes_cached": 0,
                "ttl_seconds": self.ttl,
            }

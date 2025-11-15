"""Driver statistics caching service for performance optimization."""

import json
import logging
from typing import Optional

from app.core.redis import redis_client

logger = logging.getLogger(__name__)


class DriverCacheService:
    """Cache driver statistics to reduce database load."""

    def __init__(self, redis_client):
        """
        Initialize driver cache service.

        Args:
            redis_client: Redis client instance
        """
        self.redis = redis_client
        self.ttl = 300  # 5 minutes cache

    async def get_driver_stats(self, driver_id: str) -> Optional[dict]:
        """
        Get driver stats from cache.

        Args:
            driver_id: Driver UUID as string

        Returns:
            dict | None: Driver stats or None if not cached
        """
        try:
            key = f"driver:stats:{driver_id}"
            cached = await self.redis.get(key)
            
            if cached:
                logger.debug(f"Driver stats cache HIT for {driver_id}")
                return json.loads(cached)
            
            logger.debug(f"Driver stats cache MISS for {driver_id}")
            return None
            
        except Exception as e:
            logger.warning(f"Failed to get driver stats from cache: {e}")
            return None

    async def get_driver_stats_batch(self, driver_ids: list[str]) -> dict[str, dict]:
        """
        Get multiple driver stats from cache.

        Args:
            driver_ids: List of driver UUIDs

        Returns:
            dict: Map of driver_id -> stats dict (only cached entries)
        """
        if not driver_ids:
            return {}
        
        try:
            keys = [f"driver:stats:{driver_id}" for driver_id in driver_ids]
            cached_values = await self.redis.mget(keys)
            
            results = {}
            hit_count = 0
            
            for driver_id, cached in zip(driver_ids, cached_values):
                if cached:
                    results[driver_id] = json.loads(cached)
                    hit_count += 1
            
            hit_rate = (hit_count / len(driver_ids)) * 100 if driver_ids else 0
            logger.info(
                f"Driver stats batch cache: {hit_count}/{len(driver_ids)} hits ({hit_rate:.1f}%)"
            )
            
            return results
            
        except Exception as e:
            logger.warning(f"Failed to get driver stats batch from cache: {e}")
            return {}

    async def set_driver_stats(self, driver_id: str, stats: dict) -> bool:
        """
        Cache driver stats.

        Args:
            driver_id: Driver UUID
            stats: Driver statistics dict

        Returns:
            bool: True if cached successfully
        """
        try:
            key = f"driver:stats:{driver_id}"
            await self.redis.set(
                key,
                json.dumps(stats),
                ex=self.ttl
            )
            logger.debug(f"Cached driver stats for {driver_id}")
            return True
            
        except Exception as e:
            logger.warning(f"Failed to cache driver stats: {e}")
            return False

    async def set_driver_stats_batch(self, stats_map: dict[str, dict]) -> int:
        """
        Cache multiple driver stats.

        Args:
            stats_map: Map of driver_id -> stats dict

        Returns:
            int: Number of entries cached successfully
        """
        if not stats_map:
            return 0
        
        try:
            # Use pipeline for efficiency
            pipe = self.redis.pipeline()
            
            for driver_id, stats in stats_map.items():
                key = f"driver:stats:{driver_id}"
                pipe.set(key, json.dumps(stats), ex=self.ttl)
            
            await pipe.execute()
            logger.info(f"Cached {len(stats_map)} driver stats")
            return len(stats_map)
            
        except Exception as e:
            logger.warning(f"Failed to cache driver stats batch: {e}")
            return 0

    async def invalidate_driver_stats(self, driver_id: str) -> bool:
        """
        Invalidate cached driver stats.

        Args:
            driver_id: Driver UUID

        Returns:
            bool: True if invalidated successfully
        """
        try:
            key = f"driver:stats:{driver_id}"
            await self.redis.delete(key)
            logger.debug(f"Invalidated driver stats cache for {driver_id}")
            return True
            
        except Exception as e:
            logger.warning(f"Failed to invalidate driver stats cache: {e}")
            return False

    async def get_cache_stats(self) -> dict:
        """
        Get cache statistics.

        Returns:
            dict: Cache statistics
        """
        try:
            # Count keys matching pattern
            cursor = 0
            count = 0
            
            while True:
                cursor, keys = await self.redis.scan(
                    cursor,
                    match="driver:stats:*",
                    count=100
                )
                count += len(keys)
                
                if cursor == 0:
                    break
            
            return {
                "cached_drivers": count,
                "ttl_seconds": self.ttl,
            }
            
        except Exception as e:
            logger.warning(f"Failed to get cache stats: {e}")
            return {"cached_drivers": 0, "ttl_seconds": self.ttl}

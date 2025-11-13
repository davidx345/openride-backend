"""Redis cache manager for search results."""

import hashlib
import json
import logging
from typing import Any

import redis.asyncio as redis

from app.core.config import get_settings

settings = get_settings()
logger = logging.getLogger(__name__)


class CacheManager:
    """Redis cache manager for search results."""

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
            logger.warning(f"Redis connection failed, running without cache: {e}")

    async def disconnect(self) -> None:
        """Close Redis connection."""
        if self._redis:
            await self._redis.close()
            logger.info("Redis connection closed")

    def _get_cache_key(self, prefix: str, **kwargs: Any) -> str:
        """Generate cache key from prefix and parameters."""
        sorted_params = sorted(kwargs.items())
        param_str = json.dumps(sorted_params, sort_keys=True)
        param_hash = hashlib.md5(param_str.encode()).hexdigest()[:8]
        return f"{prefix}:{param_hash}"

    async def get(self, key: str) -> Any | None:
        """Get value from cache."""
        if not self._redis:
            return None

        try:
            value = await self._redis.get(key)
            if value:
                return json.loads(value)
            return None
        except Exception as e:
            logger.warning(f"Cache get error: {e}")
            return None

    async def set(self, key: str, value: Any, ttl: int | None = None) -> bool:
        """Set value in cache with optional TTL."""
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
            logger.warning(f"Cache set error: {e}")
            return False


# Global cache manager
cache_manager = CacheManager()


async def get_cache() -> CacheManager:
    """Get cache manager instance."""
    return cache_manager

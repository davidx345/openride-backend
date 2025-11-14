"""Redis connection and utilities for caching."""

import json
import logging
from typing import Any, Optional

from redis.asyncio import Redis, from_url
from redis.exceptions import RedisError

from app.core.config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


class RedisClient:
    """Redis client wrapper with connection pooling and error handling."""

    def __init__(self):
        """Initialize Redis client."""
        self._redis: Optional[Redis] = None

    async def connect(self) -> None:
        """Establish Redis connection."""
        try:
            self._redis = from_url(
                settings.redis_url,
                encoding="utf-8",
                decode_responses=True,
                max_connections=50,
            )
            # Test connection
            await self._redis.ping()
            logger.info("Redis connected successfully")
        except RedisError as e:
            logger.error(f"Failed to connect to Redis: {e}")
            raise

    async def disconnect(self) -> None:
        """Close Redis connection."""
        if self._redis:
            await self._redis.close()
            logger.info("Redis disconnected")

    @property
    def client(self) -> Redis:
        """Get Redis client instance."""
        if not self._redis:
            raise RuntimeError("Redis client not connected. Call connect() first.")
        return self._redis

    async def get(self, key: str) -> Optional[str]:
        """
        Get value from Redis.

        Args:
            key: Cache key

        Returns:
            Value or None if not found
        """
        try:
            return await self.client.get(key)
        except RedisError as e:
            logger.error(f"Redis GET error for key {key}: {e}")
            return None

    async def set(
        self,
        key: str,
        value: str,
        ttl: Optional[int] = None,
    ) -> bool:
        """
        Set value in Redis.

        Args:
            key: Cache key
            value: Value to cache
            ttl: Time to live in seconds

        Returns:
            True if successful
        """
        try:
            if ttl:
                await self.client.setex(key, ttl, value)
            else:
                await self.client.set(key, value)
            return True
        except RedisError as e:
            logger.error(f"Redis SET error for key {key}: {e}")
            return False

    async def delete(self, *keys: str) -> int:
        """
        Delete keys from Redis.

        Args:
            keys: Keys to delete

        Returns:
            Number of keys deleted
        """
        try:
            return await self.client.delete(*keys)
        except RedisError as e:
            logger.error(f"Redis DELETE error: {e}")
            return 0

    async def get_json(self, key: str) -> Optional[Any]:
        """
        Get JSON value from Redis.

        Args:
            key: Cache key

        Returns:
            Parsed JSON or None
        """
        value = await self.get(key)
        if value:
            try:
                return json.loads(value)
            except json.JSONDecodeError as e:
                logger.error(f"JSON decode error for key {key}: {e}")
        return None

    async def set_json(
        self,
        key: str,
        value: Any,
        ttl: Optional[int] = None,
    ) -> bool:
        """
        Set JSON value in Redis.

        Args:
            key: Cache key
            value: Object to serialize as JSON
            ttl: Time to live in seconds

        Returns:
            True if successful
        """
        try:
            json_str = json.dumps(value)
            return await self.set(key, json_str, ttl)
        except (TypeError, ValueError) as e:
            logger.error(f"JSON encode error for key {key}: {e}")
            return False

    async def exists(self, key: str) -> bool:
        """
        Check if key exists.

        Args:
            key: Cache key

        Returns:
            True if exists
        """
        try:
            return bool(await self.client.exists(key))
        except RedisError as e:
            logger.error(f"Redis EXISTS error for key {key}: {e}")
            return False

    async def expire(self, key: str, ttl: int) -> bool:
        """
        Set expiration on key.

        Args:
            key: Cache key
            ttl: Time to live in seconds

        Returns:
            True if successful
        """
        try:
            return bool(await self.client.expire(key, ttl))
        except RedisError as e:
            logger.error(f"Redis EXPIRE error for key {key}: {e}")
            return False

    async def scan_keys(self, pattern: str) -> list[str]:
        """
        Scan for keys matching pattern.

        Args:
            pattern: Key pattern (e.g., "routes:*")

        Returns:
            List of matching keys
        """
        keys = []
        try:
            cursor = 0
            while True:
                cursor, partial_keys = await self.client.scan(cursor, match=pattern)
                keys.extend(partial_keys)
                if cursor == 0:
                    break
        except RedisError as e:
            logger.error(f"Redis SCAN error for pattern {pattern}: {e}")
        return keys


# Global Redis client instance
redis_client = RedisClient()


async def get_redis() -> RedisClient:
    """
    Get Redis client dependency.

    Returns:
        RedisClient instance
    """
    return redis_client

"""Redis client manager."""

from typing import Optional

import redis.asyncio as redis_async

from app.core.config import settings
from app.core.logging import get_logger

logger = get_logger(__name__)


class RedisManager:
    """Redis connection manager."""

    def __init__(self) -> None:
        """Initialize Redis manager."""
        self._client: Optional[redis_async.Redis] = None

    async def connect(self) -> None:
        """Initialize Redis connection pool."""
        try:
            self._client = redis_async.from_url(
                settings.REDIS_URL,
                max_connections=settings.REDIS_MAX_CONNECTIONS,
                socket_timeout=settings.REDIS_SOCKET_TIMEOUT,
                socket_connect_timeout=settings.REDIS_SOCKET_CONNECT_TIMEOUT,
                retry_on_timeout=settings.REDIS_RETRY_ON_TIMEOUT,
                decode_responses=settings.REDIS_DECODE_RESPONSES,
                encoding="utf-8",
            )
            # Test connection
            await self._client.ping()
            logger.info("redis_connected", url=settings.REDIS_URL)
        except Exception as e:
            logger.error("redis_connection_failed", error=str(e))
            raise

    async def disconnect(self) -> None:
        """Close Redis connection pool."""
        if self._client:
            await self._client.close()
            logger.info("redis_disconnected")

    @property
    def client(self) -> redis_async.Redis:
        """Get Redis client instance.
        
        Returns:
            Redis client
            
        Raises:
            RuntimeError: If Redis not connected
        """
        if not self._client:
            raise RuntimeError("Redis not connected. Call connect() first.")
        return self._client


# Global Redis manager instance
redis_manager = RedisManager()


async def get_redis() -> redis_async.Redis:
    """Dependency to get Redis client.
    
    Returns:
        Redis client
        
    Usage:
        @app.get("/items")
        async def get_items(redis: Redis = Depends(get_redis)):
            ...
    """
    return redis_manager.client

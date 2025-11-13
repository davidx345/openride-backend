"""Redis client configuration and management"""
import redis.asyncio as aioredis

from app.core.config import settings
from app.core.logging import get_logger

logger = get_logger(__name__)


class RedisManager:
    """Manages Redis connections"""
    
    def __init__(self):
        self._client: aioredis.Redis | None = None
    
    async def initialize(self) -> None:
        """Initialize Redis connection pool"""
        self._client = aioredis.from_url(
            settings.redis_url_with_password,
            max_connections=settings.REDIS_MAX_CONNECTIONS,
            encoding="utf-8",
            decode_responses=True,
        )
        
        # Test connection
        await self._client.ping()
        
        logger.info("Redis connection established")
    
    async def close(self) -> None:
        """Close Redis connections"""
        if self._client:
            await self._client.close()
            self._client = None
            logger.info("Redis connection closed")
    
    @property
    def client(self) -> aioredis.Redis:
        """Get Redis client"""
        if not self._client:
            raise RuntimeError("Redis not initialized. Call initialize() first.")
        return self._client


# Global Redis manager instance
redis_manager = RedisManager()


async def get_redis() -> aioredis.Redis:
    """Get Redis client (FastAPI dependency)"""
    return redis_manager.client

"""PostgreSQL LISTEN/NOTIFY handler for cache invalidation."""

import asyncio
import json
import logging
from typing import Optional

import asyncpg
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.redis import redis_client
from app.services.route_cache_service import RouteCacheService
from app.services.stats_refresh_service import stats_refresh_service

settings = get_settings()
logger = logging.getLogger(__name__)


class CacheInvalidationListener:
    """Listen to PostgreSQL notifications for cache invalidation."""

    def __init__(self):
        """Initialize cache invalidation listener."""
        self.connection: Optional[asyncpg.Connection] = None
        self.is_running = False
        self.route_cache = RouteCacheService(redis_client)

    async def connect(self) -> None:
        """Establish PostgreSQL connection for LISTEN."""
        try:
            # Parse database URL
            db_url = settings.database_url.replace(
                "postgresql+asyncpg://", "postgresql://"
            )

            self.connection = await asyncpg.connect(db_url)
            logger.info("Cache invalidation listener connected to PostgreSQL")

            # Listen to channels
            await self.connection.add_listener(
                "cache_invalidation", self._handle_cache_invalidation
            )
            await self.connection.add_listener(
                "stats_refresh", self._handle_stats_refresh
            )

            logger.info("Listening on channels: cache_invalidation, stats_refresh")

        except Exception as e:
            logger.error(f"Failed to connect listener: {e}", exc_info=True)
            raise

    async def disconnect(self) -> None:
        """Close PostgreSQL connection."""
        if self.connection:
            await self.connection.close()
            logger.info("Cache invalidation listener disconnected")

    async def _handle_cache_invalidation(
        self, connection: asyncpg.Connection, pid: int, channel: str, payload: str
    ) -> None:
        """
        Handle cache invalidation notification.

        Args:
            connection: PostgreSQL connection
            pid: Process ID
            channel: Notification channel
            payload: JSON payload
        """
        try:
            data = json.loads(payload)
            invalidation_type = data.get("type")
            operation = data.get("operation")

            logger.info(
                f"Cache invalidation: type={invalidation_type}, op={operation}, data={data}"
            )

            if invalidation_type == "route":
                # Invalidate specific route
                route_id = data.get("route_id")
                if route_id:
                    await self.route_cache.invalidate_route(route_id)

                # Also invalidate hub pair cache
                origin_hub_id = data.get("origin_hub_id")
                dest_hub_id = data.get("destination_hub_id")
                if origin_hub_id:
                    await self.route_cache.invalidate_hub_routes(origin_hub_id)
                if dest_hub_id and dest_hub_id != origin_hub_id:
                    await self.route_cache.invalidate_hub_routes(dest_hub_id)

            elif invalidation_type == "route_availability":
                # Invalidate route when availability changes
                route_id = data.get("route_id")
                if route_id:
                    await self.route_cache.invalidate_route(route_id)

            elif invalidation_type == "hub":
                # Invalidate all routes for hub
                hub_id = data.get("hub_id")
                if hub_id:
                    await self.route_cache.invalidate_hub_routes(hub_id)

            elif invalidation_type == "stop":
                # Invalidate hub routes when stop changes
                hub_id = data.get("hub_id")
                if hub_id:
                    await self.route_cache.invalidate_hub_routes(hub_id)

            else:
                logger.warning(f"Unknown invalidation type: {invalidation_type}")

        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON payload: {payload}, error: {e}")
        except Exception as e:
            logger.error(f"Error handling cache invalidation: {e}", exc_info=True)

    async def _handle_stats_refresh(
        self, connection: asyncpg.Connection, pid: int, channel: str, payload: str
    ) -> None:
        """
        Handle stats refresh notification.

        Args:
            connection: PostgreSQL connection
            pid: Process ID
            channel: Notification channel
            payload: JSON payload
        """
        try:
            data = json.loads(payload)
            logger.info(f"Stats refresh notification: {data}")

            # Trigger immediate refresh of driver stats
            await stats_refresh_service.refresh_driver_stats()

        except json.JSONDecodeError as e:
            logger.error(f"Invalid JSON payload: {payload}, error: {e}")
        except Exception as e:
            logger.error(f"Error handling stats refresh: {e}", exc_info=True)

    async def start(self) -> None:
        """Start listening for notifications."""
        if self.is_running:
            logger.warning("Cache invalidation listener already running")
            return

        await self.connect()
        self.is_running = True
        logger.info("Cache invalidation listener started")

    async def stop(self) -> None:
        """Stop listening for notifications."""
        if not self.is_running:
            return

        await self.disconnect()
        self.is_running = False
        logger.info("Cache invalidation listener stopped")


# Global instance
cache_invalidation_listener = CacheInvalidationListener()


def get_cache_invalidation_listener() -> CacheInvalidationListener:
    """
    Get cache invalidation listener instance.

    Returns:
        CacheInvalidationListener instance
    """
    return cache_invalidation_listener

"""Background job to refresh driver stats materialized view."""

import asyncio
import logging
from datetime import datetime

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import get_settings
from app.core.database import AsyncSessionLocal

settings = get_settings()
logger = logging.getLogger(__name__)


class StatsRefreshService:
    """Service for refreshing materialized views."""

    def __init__(self):
        """Initialize stats refresh service."""
        self.scheduler = AsyncIOScheduler()
        self.is_running = False

    async def refresh_driver_stats(self) -> None:
        """Refresh driver_stats_agg materialized view."""
        try:
            start_time = datetime.now()
            logger.info("Refreshing driver_stats_agg materialized view...")

            async with AsyncSessionLocal() as db:
                await db.execute(
                    text("REFRESH MATERIALIZED VIEW CONCURRENTLY driver_stats_agg")
                )
                await db.commit()

            duration = (datetime.now() - start_time).total_seconds()
            logger.info(
                f"driver_stats_agg refreshed successfully in {duration:.2f}s"
            )

        except Exception as e:
            logger.error(f"Failed to refresh driver_stats_agg: {e}", exc_info=True)

    def start(self) -> None:
        """Start background refresh jobs."""
        if self.is_running:
            logger.warning("Stats refresh service already running")
            return

        # Schedule driver stats refresh every 5 minutes
        self.scheduler.add_job(
            self.refresh_driver_stats,
            "interval",
            minutes=5,
            id="refresh_driver_stats",
            replace_existing=True,
        )

        self.scheduler.start()
        self.is_running = True
        logger.info("Stats refresh service started (interval: 5 minutes)")

    def stop(self) -> None:
        """Stop background refresh jobs."""
        if not self.is_running:
            return

        self.scheduler.shutdown()
        self.is_running = False
        logger.info("Stats refresh service stopped")


# Global instance
stats_refresh_service = StatsRefreshService()


def get_stats_refresh_service() -> StatsRefreshService:
    """
    Get stats refresh service instance.

    Returns:
        StatsRefreshService instance
    """
    return stats_refresh_service

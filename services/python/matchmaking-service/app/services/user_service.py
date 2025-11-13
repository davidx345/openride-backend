"""User service client for external API calls."""

import logging
from typing import Any

import httpx

from app.core.config import get_settings
from app.core.exceptions import ExternalServiceError

settings = get_settings()
logger = logging.getLogger(__name__)


class UserService:
    """Client for User Service API."""

    def __init__(self) -> None:
        """Initialize user service client."""
        self.base_url = settings.user_service_url
        self.timeout = 5.0  # 5 second timeout

    async def get_driver(self, driver_id: str) -> dict[str, Any] | None:
        """
        Get driver information by ID.

        Args:
            driver_id: Driver UUID

        Returns:
            dict | None: Driver data or None if not found
        """
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.get(f"{self.base_url}/v1/users/{driver_id}")

                if response.status_code == 404:
                    return None

                response.raise_for_status()
                return response.json()

        except httpx.HTTPStatusError as e:
            logger.error(f"HTTP error fetching driver {driver_id}: {e}")
            return None
        except Exception as e:
            logger.warning(f"Failed to fetch driver {driver_id}: {e}")
            return None

    async def get_drivers_batch(self, driver_ids: list[str]) -> dict[str, dict[str, Any]]:
        """
        Get multiple drivers in batch (if supported by User Service).

        Args:
            driver_ids: List of driver UUIDs

        Returns:
            dict: Map of driver_id -> driver data
        """
        # For now, fetch individually (can be optimized with batch endpoint)
        results = {}

        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                for driver_id in driver_ids:
                    try:
                        response = await client.get(f"{self.base_url}/v1/users/{driver_id}")
                        if response.status_code == 200:
                            results[driver_id] = response.json()
                    except Exception as e:
                        logger.warning(f"Failed to fetch driver {driver_id}: {e}")
                        continue

        except Exception as e:
            logger.error(f"Batch driver fetch failed: {e}")

        return results

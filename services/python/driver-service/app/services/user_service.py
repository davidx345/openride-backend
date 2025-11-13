"""
User service for external API calls to User Service.
"""
import logging
from typing import Optional
from uuid import UUID

import httpx

from app.core.config import settings
from app.core.exceptions import KYCNotVerifiedException

logger = logging.getLogger(__name__)


class UserService:
    """Service for user-related operations."""
    
    def __init__(self):
        """Initialize user service."""
        self.base_url = settings.USER_SERVICE_URL
        self.timeout = 5.0
    
    async def verify_driver_kyc(self, driver_id: UUID, token: str) -> bool:
        """
        Verify driver KYC status via User Service API.
        
        Args:
            driver_id: Driver's user ID
            token: JWT token for authentication
            
        Returns:
            True if KYC is verified
            
        Raises:
            KYCNotVerifiedException: If KYC is not verified
            Exception: If API call fails
        """
        try:
            async with httpx.AsyncClient(timeout=self.timeout) as client:
                response = await client.get(
                    f"{self.base_url}/v1/users/{driver_id}",
                    headers={"Authorization": f"Bearer {token}"}
                )
                
                if response.status_code == 200:
                    user_data = response.json()
                    kyc_status = user_data.get("kyc_status", "NONE")
                    role = user_data.get("role", "RIDER")
                    
                    if role not in ["DRIVER", "ADMIN"]:
                        raise KYCNotVerifiedException(
                            "User is not a driver"
                        )
                    
                    if kyc_status != "VERIFIED":
                        raise KYCNotVerifiedException(
                            f"Driver KYC status is {kyc_status}. Must be VERIFIED to create routes."
                        )
                    
                    return True
                else:
                    logger.warning(
                        f"Failed to verify KYC for driver {driver_id}: {response.status_code}"
                    )
                    # In development, allow if user service is not available
                    if settings.ENVIRONMENT == "development":
                        logger.warning("Development mode: allowing without KYC verification")
                        return True
                    raise Exception(f"Failed to verify KYC: {response.status_code}")
                    
        except httpx.RequestError as e:
            logger.error(f"Request error while verifying KYC: {e}")
            # In development, allow if user service is not available
            if settings.ENVIRONMENT == "development":
                logger.warning("Development mode: allowing without KYC verification")
                return True
            raise Exception(f"Failed to connect to User Service: {e}")

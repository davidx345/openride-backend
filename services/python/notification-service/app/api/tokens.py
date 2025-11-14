"""FCM token management API endpoints."""
import logging
from typing import List
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_
from app.database import get_db_session
from app.auth import get_current_user
from app.models import FCMToken
from app.schemas import DeviceTokenCreate, DeviceTokenResponse
from app.services.notification_service import NotificationService

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/v1/notifications/tokens", tags=["tokens"])


@router.post("", response_model=DeviceTokenResponse, status_code=status.HTTP_201_CREATED)
async def register_device_token(
    request: DeviceTokenCreate,
    current_user: UUID = Depends(get_current_user),
    db: AsyncSession = Depends(get_db_session),
) -> DeviceTokenResponse:
    """
    Register a new FCM device token for push notifications.
    """
    notification_service = NotificationService()

    try:
        token = await notification_service.register_device_token(
            db=db,
            user_id=current_user,
            platform=request.platform,
            fcm_token=request.fcm_token,
        )

        return DeviceTokenResponse.model_validate(token)
    except Exception as e:
        logger.error(f"Failed to register device token: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to register device token: {str(e)}",
        )


@router.get("", response_model=List[DeviceTokenResponse])
async def get_user_tokens(
    current_user: UUID = Depends(get_current_user),
    db: AsyncSession = Depends(get_db_session),
) -> List[DeviceTokenResponse]:
    """
    Get all device tokens for a user.
    """
    try:
        result = await db.execute(
            select(FCMToken).where(FCMToken.user_id == current_user)
        )
        tokens = result.scalars().all()

        return [DeviceTokenResponse.model_validate(token) for token in tokens]
    except Exception as e:
        logger.error(f"Failed to get user tokens: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to get user tokens: {str(e)}",
        )


@router.delete("/{token_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_device_token(
    token_id: UUID,
    current_user: UUID = Depends(get_current_user),
    db: AsyncSession = Depends(get_db_session),
) -> None:
    """
    Delete a device token (deactivate).
    """
    try:
        result = await db.execute(
            select(FCMToken).where(
                and_(
                    FCMToken.id == token_id,
                    FCMToken.user_id == current_user,
                )
            )
        )
        token = result.scalar_one_or_none()

        if not token:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Token not found",
            )

        token.is_active = False
        await db.commit()

        logger.info(f"Deactivated device token {token_id} for user {current_user}")
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to delete device token: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to delete device token: {str(e)}",
        )

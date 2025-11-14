"""User notification preferences API endpoints."""
import logging
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db_session
from app.auth import get_current_user
from app.schemas import UserNotificationPreferenceUpdate, UserNotificationPreferenceResponse
from app.services.preference_service import PreferenceService

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/v1/notifications/preferences", tags=["preferences"])


@router.get("", response_model=UserNotificationPreferenceResponse)
async def get_preferences(
    current_user: UUID = Depends(get_current_user),
    db: AsyncSession = Depends(get_db_session),
) -> UserNotificationPreferenceResponse:
    """
    Get user's notification preferences.
    """
    preference_service = PreferenceService()

    try:
        preferences = await preference_service.get_preferences(db=db, user_id=current_user)

        if not preferences:
            # Create default preferences if they don't exist
            preferences = await preference_service.create_default_preferences(
                db=db, user_id=current_user
            )

        return UserNotificationPreferenceResponse.model_validate(preferences)
    except Exception as e:
        logger.error(f"Failed to get preferences: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to get preferences: {str(e)}",
        )


@router.patch("", response_model=UserNotificationPreferenceResponse)
async def update_preferences(
    request: UserNotificationPreferenceUpdate,
    current_user: UUID = Depends(get_current_user),
    db: AsyncSession = Depends(get_db_session),
) -> UserNotificationPreferenceResponse:
    """
    Update user's notification preferences.
    """
    preference_service = PreferenceService()

    try:
        preferences = await preference_service.update_preferences(
            db=db,
            user_id=current_user,
            preference_update=request,
        )

        return UserNotificationPreferenceResponse.model_validate(preferences)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(e),
        )
    except Exception as e:
        logger.error(f"Failed to update preferences: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to update preferences: {str(e)}",
        )

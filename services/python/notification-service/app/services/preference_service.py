"""User notification preference management service."""
import logging
from typing import Optional
from uuid import UUID
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.models import UserNotificationPreference, NotificationChannel
from app.schemas import UserNotificationPreferenceUpdate

logger = logging.getLogger(__name__)


class PreferenceService:
    """Service for managing user notification preferences."""

    async def get_preferences(
        self,
        db: AsyncSession,
        user_id: UUID,
    ) -> Optional[UserNotificationPreference]:
        """
        Get user notification preferences.

        Args:
            db: Database session
            user_id: User ID

        Returns:
            UserNotificationPreference or None
        """
        result = await db.execute(
            select(UserNotificationPreference).where(
                UserNotificationPreference.user_id == user_id
            )
        )
        return result.scalar_one_or_none()

    async def create_default_preferences(
        self,
        db: AsyncSession,
        user_id: UUID,
    ) -> UserNotificationPreference:
        """
        Create default notification preferences for new user.

        Args:
            db: Database session
            user_id: User ID

        Returns:
            Created UserNotificationPreference
        """
        # Check if preferences already exist
        existing = await self.get_preferences(db, user_id)
        if existing:
            return existing

        preferences = UserNotificationPreference(
            user_id=user_id,
            push_enabled=True,
            sms_enabled=True,
            email_enabled=True,
        )

        db.add(preferences)
        await db.commit()
        await db.refresh(preferences)

        logger.info(f"Created default preferences for user {user_id}")
        return preferences

    async def update_preferences(
        self,
        db: AsyncSession,
        user_id: UUID,
        preference_update: UserNotificationPreferenceUpdate,
    ) -> UserNotificationPreference:
        """
        Update user notification preferences.

        Args:
            db: Database session
            user_id: User ID
            preference_update: Preference update data

        Returns:
            Updated UserNotificationPreference

        Raises:
            ValueError: If preferences not found
        """
        preferences = await self.get_preferences(db, user_id)

        if not preferences:
            raise ValueError(f"Preferences not found for user {user_id}")

        # Update fields
        update_data = preference_update.model_dump(exclude_unset=True)
        for field, value in update_data.items():
            setattr(preferences, field, value)

        await db.commit()
        await db.refresh(preferences)

        logger.info(f"Updated preferences for user {user_id}: {update_data}")
        return preferences

    async def is_channel_enabled(
        self,
        db: AsyncSession,
        user_id: UUID,
        channel: NotificationChannel,
    ) -> bool:
        """
        Check if notification channel is enabled for user.

        Args:
            db: Database session
            user_id: User ID
            channel: Notification channel

        Returns:
            True if channel is enabled, False otherwise
        """
        preferences = await self.get_preferences(db, user_id)

        # If no preferences, create defaults (all enabled)
        if not preferences:
            preferences = await self.create_default_preferences(db, user_id)

        # Check channel status
        if channel == NotificationChannel.PUSH:
            return preferences.push_enabled
        elif channel == NotificationChannel.SMS:
            return preferences.sms_enabled
        elif channel == NotificationChannel.EMAIL:
            return preferences.email_enabled

        return False

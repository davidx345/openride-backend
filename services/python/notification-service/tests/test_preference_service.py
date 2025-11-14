"""Tests for preference service."""
import pytest
from uuid import uuid4
from app.services.preference_service import PreferenceService
from app.models import NotificationChannel, NotificationType


class TestPreferenceService:
    """Test notification preference service."""

    @pytest.mark.asyncio
    async def test_get_preferences_existing(self, db_session, test_user_id):
        """Test getting existing preferences."""
        # Create test preferences
        from app.models import NotificationPreference
        pref = NotificationPreference(
            user_id=test_user_id,
            push_enabled=True,
            sms_enabled=False,
            email_enabled=True,
        )
        db_session.add(pref)
        await db_session.commit()

        service = PreferenceService(db_session)
        result = await service.get_preferences(test_user_id)

        assert result is not None
        assert result.user_id == test_user_id
        assert result.push_enabled is True
        assert result.sms_enabled is False

    @pytest.mark.asyncio
    async def test_get_preferences_creates_default(self, db_session):
        """Test getting preferences creates default if not exists."""
        new_user_id = uuid4()
        service = PreferenceService(db_session)
        result = await service.get_preferences(new_user_id)

        assert result is not None
        assert result.user_id == new_user_id
        assert result.push_enabled is True  # Default
        assert result.sms_enabled is True  # Default
        assert result.email_enabled is True  # Default

    @pytest.mark.asyncio
    async def test_create_default_preferences(self, db_session):
        """Test creating default preferences."""
        user_id = uuid4()
        service = PreferenceService(db_session)
        result = await service.create_default_preferences(user_id)

        assert result is not None
        assert result.user_id == user_id
        assert result.push_enabled is True
        assert result.sms_enabled is True
        assert result.email_enabled is True
        assert result.quiet_hours_start is None
        assert result.quiet_hours_end is None

    @pytest.mark.asyncio
    async def test_update_preferences_all_fields(self, db_session, test_user_id):
        """Test updating all preference fields."""
        # Create initial preferences
        from app.models import NotificationPreference
        pref = NotificationPreference(user_id=test_user_id)
        db_session.add(pref)
        await db_session.commit()

        service = PreferenceService(db_session)
        result = await service.update_preferences(
            test_user_id,
            push_enabled=False,
            sms_enabled=False,
            email_enabled=True,
            quiet_hours_start="22:00",
            quiet_hours_end="07:00",
            disabled_notification_types=["booking_confirmed", "payment_success"],
        )

        assert result.push_enabled is False
        assert result.sms_enabled is False
        assert result.email_enabled is True
        assert result.quiet_hours_start is not None
        assert "booking_confirmed" in result.disabled_notification_types

    @pytest.mark.asyncio
    async def test_update_preferences_partial(self, db_session, test_user_id):
        """Test partial preference updates."""
        from app.models import NotificationPreference
        pref = NotificationPreference(
            user_id=test_user_id,
            push_enabled=True,
            sms_enabled=True,
            email_enabled=True,
        )
        db_session.add(pref)
        await db_session.commit()

        service = PreferenceService(db_session)
        result = await service.update_preferences(
            test_user_id,
            push_enabled=False,  # Only update this
        )

        assert result.push_enabled is False
        assert result.sms_enabled is True  # Unchanged
        assert result.email_enabled is True  # Unchanged

    @pytest.mark.asyncio
    async def test_is_channel_enabled_push(self, db_session, test_user_id):
        """Test checking if push channel is enabled."""
        from app.models import NotificationPreference
        pref = NotificationPreference(
            user_id=test_user_id,
            push_enabled=True,
            sms_enabled=False,
            email_enabled=False,
        )
        db_session.add(pref)
        await db_session.commit()

        service = PreferenceService(db_session)
        
        assert await service.is_channel_enabled(test_user_id, NotificationChannel.PUSH) is True
        assert await service.is_channel_enabled(test_user_id, NotificationChannel.SMS) is False
        assert await service.is_channel_enabled(test_user_id, NotificationChannel.EMAIL) is False

    @pytest.mark.asyncio
    async def test_is_channel_enabled_no_preferences(self, db_session):
        """Test checking channel when no preferences exist."""
        user_id = uuid4()
        service = PreferenceService(db_session)

        # Should create default preferences and return True
        result = await service.is_channel_enabled(user_id, NotificationChannel.PUSH)
        assert result is True

    @pytest.mark.asyncio
    async def test_is_notification_type_enabled(self, db_session, test_user_id):
        """Test checking if notification type is enabled."""
        from app.models import NotificationPreference
        pref = NotificationPreference(
            user_id=test_user_id,
            disabled_notification_types=["booking_confirmed", "payment_success"],
        )
        db_session.add(pref)
        await db_session.commit()

        service = PreferenceService(db_session)

        assert await service.is_notification_type_enabled(
            test_user_id, NotificationType.BOOKING_CONFIRMED
        ) is False
        assert await service.is_notification_type_enabled(
            test_user_id, NotificationType.DRIVER_ARRIVED
        ) is True

    @pytest.mark.asyncio
    async def test_quiet_hours_check(self, db_session, test_user_id):
        """Test quiet hours validation."""
        from app.models import NotificationPreference
        from datetime import time
        pref = NotificationPreference(
            user_id=test_user_id,
            quiet_hours_start=time(22, 0),  # 10 PM
            quiet_hours_end=time(7, 0),  # 7 AM
        )
        db_session.add(pref)
        await db_session.commit()

        service = PreferenceService(db_session)
        prefs = await service.get_preferences(test_user_id)

        assert prefs.quiet_hours_start == time(22, 0)
        assert prefs.quiet_hours_end == time(7, 0)

    @pytest.mark.asyncio
    async def test_disable_all_channels(self, db_session, test_user_id):
        """Test disabling all notification channels."""
        from app.models import NotificationPreference
        pref = NotificationPreference(user_id=test_user_id)
        db_session.add(pref)
        await db_session.commit()

        service = PreferenceService(db_session)
        result = await service.update_preferences(
            test_user_id,
            push_enabled=False,
            sms_enabled=False,
            email_enabled=False,
        )

        assert result.push_enabled is False
        assert result.sms_enabled is False
        assert result.email_enabled is False

    @pytest.mark.asyncio
    async def test_disable_all_notification_types(self, db_session, test_user_id):
        """Test disabling all notification types."""
        from app.models import NotificationPreference
        pref = NotificationPreference(user_id=test_user_id)
        db_session.add(pref)
        await db_session.commit()

        all_types = [t.value for t in NotificationType]

        service = PreferenceService(db_session)
        result = await service.update_preferences(
            test_user_id,
            disabled_notification_types=all_types,
        )

        assert len(result.disabled_notification_types) == len(all_types)

    @pytest.mark.asyncio
    async def test_enable_specific_notification_types(self, db_session, test_user_id):
        """Test enabling specific notification types by removing from disabled list."""
        from app.models import NotificationPreference
        pref = NotificationPreference(
            user_id=test_user_id,
            disabled_notification_types=["booking_confirmed", "payment_success", "driver_arrived"],
        )
        db_session.add(pref)
        await db_session.commit()

        service = PreferenceService(db_session)
        result = await service.update_preferences(
            test_user_id,
            disabled_notification_types=["booking_confirmed"],  # Re-enable others
        )

        assert "booking_confirmed" in result.disabled_notification_types
        assert "payment_success" not in result.disabled_notification_types
        assert "driver_arrived" not in result.disabled_notification_types

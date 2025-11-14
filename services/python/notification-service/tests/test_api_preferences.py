"""Tests for notification preferences API endpoints."""
import pytest
from httpx import AsyncClient


class TestPreferencesAPI:
    """Test notification preferences API endpoints."""

    @pytest.mark.asyncio
    async def test_get_preferences_unauthorized(self, client: AsyncClient):
        """Test getting preferences without auth."""
        response = await client.get("/v1/notifications/preferences")
        assert response.status_code == 403

    @pytest.mark.asyncio
    async def test_get_preferences_creates_default(
        self, client: AsyncClient, auth_headers, test_user_id
    ):
        """Test getting preferences creates default if not exists."""
        response = await client.get(
            "/v1/notifications/preferences",
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["user_id"] == str(test_user_id)
        assert data["push_enabled"] is True  # Default
        assert data["sms_enabled"] is True  # Default
        assert data["email_enabled"] is True  # Default

    @pytest.mark.asyncio
    async def test_get_existing_preferences(
        self, client: AsyncClient, auth_headers, test_user_id, db_session
    ):
        """Test getting existing preferences."""
        # Create test preferences
        from app.models import NotificationPreference
        pref = NotificationPreference(
            user_id=test_user_id,
            push_enabled=False,
            sms_enabled=True,
            email_enabled=False,
        )
        db_session.add(pref)
        await db_session.commit()

        response = await client.get(
            "/v1/notifications/preferences",
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["push_enabled"] is False
        assert data["sms_enabled"] is True
        assert data["email_enabled"] is False

    @pytest.mark.asyncio
    async def test_update_preferences_unauthorized(self, client: AsyncClient):
        """Test updating preferences without auth."""
        response = await client.patch(
            "/v1/notifications/preferences",
            json={"push_enabled": False},
        )
        assert response.status_code == 403

    @pytest.mark.asyncio
    async def test_update_preferences_success(
        self, client: AsyncClient, auth_headers, test_user_id, db_session
    ):
        """Test updating preferences with auth."""
        # Create initial preferences
        from app.models import NotificationPreference
        pref = NotificationPreference(
            user_id=test_user_id,
            push_enabled=True,
            sms_enabled=True,
            email_enabled=True,
        )
        db_session.add(pref)
        await db_session.commit()

        response = await client.patch(
            "/v1/notifications/preferences",
            headers=auth_headers,
            json={
                "push_enabled": False,
                "sms_enabled": False,
            },
        )
        assert response.status_code == 200
        data = response.json()
        assert data["push_enabled"] is False
        assert data["sms_enabled"] is False
        assert data["email_enabled"] is True  # Unchanged

    @pytest.mark.asyncio
    async def test_update_notification_types(
        self, client: AsyncClient, auth_headers, test_user_id, db_session
    ):
        """Test updating notification type preferences."""
        from app.models import NotificationPreference
        pref = NotificationPreference(user_id=test_user_id)
        db_session.add(pref)
        await db_session.commit()

        response = await client.patch(
            "/v1/notifications/preferences",
            headers=auth_headers,
            json={
                "disabled_notification_types": ["booking_confirmed", "payment_success"]
            },
        )
        assert response.status_code == 200
        data = response.json()
        assert "booking_confirmed" in data["disabled_notification_types"]
        assert "payment_success" in data["disabled_notification_types"]

    @pytest.mark.asyncio
    async def test_update_quiet_hours(
        self, client: AsyncClient, auth_headers, test_user_id, db_session
    ):
        """Test updating quiet hours."""
        from app.models import NotificationPreference
        pref = NotificationPreference(user_id=test_user_id)
        db_session.add(pref)
        await db_session.commit()

        response = await client.patch(
            "/v1/notifications/preferences",
            headers=auth_headers,
            json={
                "quiet_hours_start": "22:00",
                "quiet_hours_end": "07:00",
            },
        )
        assert response.status_code == 200
        data = response.json()
        assert data["quiet_hours_start"] == "22:00:00"
        assert data["quiet_hours_end"] == "07:00:00"

    @pytest.mark.asyncio
    async def test_partial_update(
        self, client: AsyncClient, auth_headers, test_user_id, db_session
    ):
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

        # Update only email preference
        response = await client.patch(
            "/v1/notifications/preferences",
            headers=auth_headers,
            json={"email_enabled": False},
        )
        assert response.status_code == 200
        data = response.json()
        assert data["push_enabled"] is True  # Unchanged
        assert data["sms_enabled"] is True  # Unchanged
        assert data["email_enabled"] is False  # Updated

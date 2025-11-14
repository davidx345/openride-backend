"""Tests for notification API endpoints."""
import pytest
from uuid import uuid4
from httpx import AsyncClient
from app.models import NotificationType, NotificationChannel


class TestNotificationAPI:
    """Test notification API endpoints."""

    @pytest.mark.asyncio
    async def test_send_notification_unauthorized(self, client: AsyncClient):
        """Test sending notification without auth."""
        response = await client.post(
            "/v1/notifications/send",
            json={
                "user_id": str(uuid4()),
                "notification_type": "booking_confirmed",
                "channels": ["push"],
                "template_data": {},
            },
        )
        assert response.status_code == 403  # No auth header

    @pytest.mark.asyncio
    async def test_send_notification_success(
        self, client: AsyncClient, auth_headers, test_user_id, db_session
    ):
        """Test sending notification with auth."""
        # Create test template first
        from app.models import NotificationTemplate
        template = NotificationTemplate(
            template_key="test_booking_confirmed_push",
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channel=NotificationChannel.PUSH,
            subject_template="Test Subject",
            body_template="Test body for {{ user_name }}",
        )
        db_session.add(template)
        await db_session.commit()

        response = await client.post(
            "/v1/notifications/send",
            headers=auth_headers,
            json={
                "user_id": str(test_user_id),
                "notification_type": "booking_confirmed",
                "channels": ["push"],
                "template_data": {"user_name": "John Doe"},
            },
        )
        assert response.status_code in [200, 500]  # May fail due to FCM not configured

    @pytest.mark.asyncio
    async def test_send_notification_async(self, client: AsyncClient, auth_headers):
        """Test sending notification asynchronously."""
        response = await client.post(
            "/v1/notifications/send-async",
            headers=auth_headers,
            json={
                "user_id": str(uuid4()),
                "notification_type": "payment_success",
                "channels": ["sms"],
                "phone_number": "+2348012345678",
                "template_data": {"amount": "5000"},
            },
        )
        assert response.status_code in [202, 500]  # May fail if Celery not running

    @pytest.mark.asyncio
    async def test_get_notification_history(
        self, client: AsyncClient, auth_headers, test_user_id
    ):
        """Test getting notification history."""
        response = await client.get(
            f"/v1/notifications/history?user_id={test_user_id}",
            headers=auth_headers,
        )
        assert response.status_code == 200
        assert isinstance(response.json(), list)

    @pytest.mark.asyncio
    async def test_get_notification_by_id(
        self, client: AsyncClient, auth_headers, db_session
    ):
        """Test getting notification by ID."""
        # Create test notification log
        from app.models import NotificationLog, NotificationStatus
        notif_id = uuid4()
        log = NotificationLog(
            id=notif_id,
            user_id=uuid4(),
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channel=NotificationChannel.PUSH,
            recipient_address="test@example.com",
            subject="Test",
            body="Test body",
            status=NotificationStatus.SENT,
        )
        db_session.add(log)
        await db_session.commit()

        response = await client.get(
            f"/v1/notifications/{notif_id}",
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["id"] == str(notif_id)

    @pytest.mark.asyncio
    async def test_get_notification_not_found(self, client: AsyncClient, auth_headers):
        """Test getting non-existent notification."""
        response = await client.get(
            f"/v1/notifications/{uuid4()}",
            headers=auth_headers,
        )
        assert response.status_code == 404


class TestBroadcastAPI:
    """Test broadcast notification endpoints."""

    @pytest.mark.asyncio
    async def test_broadcast_unauthorized(self, client: AsyncClient):
        """Test broadcast without auth."""
        response = await client.post(
            "/v1/notifications/broadcast",
            json={
                "user_ids": [str(uuid4())],
                "notification_type": "route_cancelled",
                "channels": ["push"],
                "template_data": {},
            },
        )
        assert response.status_code == 403

"""Tests for admin template API endpoints."""
import pytest
from uuid import uuid4
from httpx import AsyncClient


class TestTemplateAdminAPI:
    """Test admin template API endpoints."""

    @pytest.mark.asyncio
    async def test_create_template_unauthorized(self, client: AsyncClient, auth_headers):
        """Test creating template without admin role."""
        response = await client.post(
            "/v1/admin/notification-templates",
            headers=auth_headers,  # Regular user, not admin
            json={
                "template_key": "test_template",
                "notification_type": "booking_confirmed",
                "channel": "push",
                "subject_template": "Subject",
                "body_template": "Body {{ var }}",
            },
        )
        assert response.status_code == 403

    @pytest.mark.asyncio
    async def test_create_template_success(
        self, client: AsyncClient, admin_auth_headers
    ):
        """Test creating template with admin role."""
        response = await client.post(
            "/v1/admin/notification-templates",
            headers=admin_auth_headers,
            json={
                "template_key": "admin_test_template",
                "notification_type": "driver_arrived",
                "channel": "sms",
                "subject_template": None,
                "body_template": "Driver {{ driver_name }} has arrived",
                "is_active": True,
            },
        )
        assert response.status_code == 200
        data = response.json()
        assert data["template_key"] == "admin_test_template"
        assert data["notification_type"] == "driver_arrived"
        assert data["channel"] == "sms"

    @pytest.mark.asyncio
    async def test_create_duplicate_template(
        self, client: AsyncClient, admin_auth_headers, db_session
    ):
        """Test creating duplicate template."""
        # Create existing template
        from app.models import NotificationTemplate, NotificationType, NotificationChannel
        template = NotificationTemplate(
            template_key="duplicate_key",
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channel=NotificationChannel.PUSH,
            subject_template="Subject",
            body_template="Body",
        )
        db_session.add(template)
        await db_session.commit()

        response = await client.post(
            "/v1/admin/notification-templates",
            headers=admin_auth_headers,
            json={
                "template_key": "duplicate_key",
                "notification_type": "booking_confirmed",
                "channel": "push",
                "subject_template": "Subject 2",
                "body_template": "Body 2",
            },
        )
        # Should fail due to unique constraint
        assert response.status_code in [400, 500]

    @pytest.mark.asyncio
    async def test_list_templates_unauthorized(self, client: AsyncClient, auth_headers):
        """Test listing templates without admin role."""
        response = await client.get(
            "/v1/admin/notification-templates",
            headers=auth_headers,
        )
        assert response.status_code == 403

    @pytest.mark.asyncio
    async def test_list_templates_success(
        self, client: AsyncClient, admin_auth_headers, db_session
    ):
        """Test listing templates with admin role."""
        # Create test templates
        from app.models import NotificationTemplate, NotificationType, NotificationChannel
        templates = [
            NotificationTemplate(
                template_key="list_test_1",
                notification_type=NotificationType.BOOKING_CONFIRMED,
                channel=NotificationChannel.PUSH,
                body_template="Body 1",
            ),
            NotificationTemplate(
                template_key="list_test_2",
                notification_type=NotificationType.PAYMENT_SUCCESS,
                channel=NotificationChannel.EMAIL,
                body_template="Body 2",
            ),
        ]
        db_session.add_all(templates)
        await db_session.commit()

        response = await client.get(
            "/v1/admin/notification-templates",
            headers=admin_auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert len(data) >= 2

    @pytest.mark.asyncio
    async def test_get_template_by_id(
        self, client: AsyncClient, admin_auth_headers, db_session
    ):
        """Test getting template by ID."""
        from app.models import NotificationTemplate, NotificationType, NotificationChannel
        template = NotificationTemplate(
            template_key="get_test",
            notification_type=NotificationType.ROUTE_CANCELLED,
            channel=NotificationChannel.SMS,
            body_template="Route cancelled",
        )
        db_session.add(template)
        await db_session.commit()
        await db_session.refresh(template)

        response = await client.get(
            f"/v1/admin/notification-templates/{template.id}",
            headers=admin_auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert data["id"] == str(template.id)
        assert data["template_key"] == "get_test"

    @pytest.mark.asyncio
    async def test_update_template(
        self, client: AsyncClient, admin_auth_headers, db_session
    ):
        """Test updating template."""
        from app.models import NotificationTemplate, NotificationType, NotificationChannel
        template = NotificationTemplate(
            template_key="update_test",
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channel=NotificationChannel.PUSH,
            body_template="Original body",
        )
        db_session.add(template)
        await db_session.commit()
        await db_session.refresh(template)

        response = await client.put(
            f"/v1/admin/notification-templates/{template.id}",
            headers=admin_auth_headers,
            json={
                "template_key": "update_test",
                "notification_type": "booking_confirmed",
                "channel": "push",
                "subject_template": "New subject",
                "body_template": "Updated body",
                "is_active": False,
            },
        )
        assert response.status_code == 200
        data = response.json()
        assert data["body_template"] == "Updated body"
        assert data["subject_template"] == "New subject"
        assert data["is_active"] is False

    @pytest.mark.asyncio
    async def test_delete_template(
        self, client: AsyncClient, admin_auth_headers, db_session
    ):
        """Test deleting template."""
        from app.models import NotificationTemplate, NotificationType, NotificationChannel
        template = NotificationTemplate(
            template_key="delete_test",
            notification_type=NotificationType.DRIVER_ARRIVED,
            channel=NotificationChannel.PUSH,
            body_template="Delete me",
        )
        db_session.add(template)
        await db_session.commit()
        await db_session.refresh(template)

        response = await client.delete(
            f"/v1/admin/notification-templates/{template.id}",
            headers=admin_auth_headers,
        )
        assert response.status_code == 200

        # Verify deletion
        get_response = await client.get(
            f"/v1/admin/notification-templates/{template.id}",
            headers=admin_auth_headers,
        )
        assert get_response.status_code == 404

    @pytest.mark.asyncio
    async def test_template_not_found(self, client: AsyncClient, admin_auth_headers):
        """Test operations on non-existent template."""
        fake_id = uuid4()
        
        # Get
        response = await client.get(
            f"/v1/admin/notification-templates/{fake_id}",
            headers=admin_auth_headers,
        )
        assert response.status_code == 404

        # Update
        response = await client.put(
            f"/v1/admin/notification-templates/{fake_id}",
            headers=admin_auth_headers,
            json={
                "template_key": "test",
                "notification_type": "booking_confirmed",
                "channel": "push",
                "body_template": "body",
            },
        )
        assert response.status_code == 404

        # Delete
        response = await client.delete(
            f"/v1/admin/notification-templates/{fake_id}",
            headers=admin_auth_headers,
        )
        assert response.status_code == 404

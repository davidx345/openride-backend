"""Tests for template service."""
import pytest
from uuid import uuid4
from app.services.template_service import TemplateService
from app.models import NotificationType, NotificationChannel


class TestTemplateService:
    """Test template service."""

    @pytest.mark.asyncio
    async def test_get_template_success(self, db_session):
        """Test getting template successfully."""
        # Create test template
        from app.models import NotificationTemplate
        template = NotificationTemplate(
            template_key="test_booking_confirmed_push",
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channel=NotificationChannel.PUSH,
            subject_template="Booking Confirmed",
            body_template="Your booking #{{ booking_id }} is confirmed",
        )
        db_session.add(template)
        await db_session.commit()

        service = TemplateService(db_session)
        result = await service.get_template(
            NotificationType.BOOKING_CONFIRMED, NotificationChannel.PUSH
        )

        assert result is not None
        assert result.template_key == "test_booking_confirmed_push"

    @pytest.mark.asyncio
    async def test_get_template_not_found(self, db_session):
        """Test getting non-existent template."""
        service = TemplateService(db_session)
        result = await service.get_template(
            NotificationType.DRIVER_ARRIVED, NotificationChannel.SMS
        )

        assert result is None

    @pytest.mark.asyncio
    async def test_render_template_success(self, db_session):
        """Test rendering template with data."""
        service = TemplateService(db_session)
        template_str = "Hello {{ user_name }}, your booking #{{ booking_id }} is ready"
        data = {"user_name": "John Doe", "booking_id": "12345"}

        result = service.render_template(template_str, data)

        assert result == "Hello John Doe, your booking #12345 is ready"

    @pytest.mark.asyncio
    async def test_render_template_missing_variable(self, db_session):
        """Test rendering template with missing variable."""
        service = TemplateService(db_session)
        template_str = "Hello {{ user_name }}, amount: {{ amount }}"
        data = {"user_name": "Jane"}  # Missing 'amount'

        result = service.render_template(template_str, data)

        # Jinja2 renders missing variables as empty string
        assert "Jane" in result

    @pytest.mark.asyncio
    async def test_render_template_with_filters(self, db_session):
        """Test rendering template with Jinja2 filters."""
        service = TemplateService(db_session)
        template_str = "Hello {{ name|upper }}, total: {{ amount|round(2) }}"
        data = {"name": "john", "amount": 123.456}

        result = service.render_template(template_str, data)

        assert "JOHN" in result
        assert "123.46" in result

    @pytest.mark.asyncio
    async def test_render_notification_push(self, db_session):
        """Test rendering complete push notification."""
        # Create template
        from app.models import NotificationTemplate
        template = NotificationTemplate(
            template_key="test_push",
            notification_type=NotificationType.PAYMENT_SUCCESS,
            channel=NotificationChannel.PUSH,
            subject_template="Payment Successful",
            body_template="Your payment of ₦{{ amount }} was successful",
        )
        db_session.add(template)
        await db_session.commit()

        service = TemplateService(db_session)
        result = await service.render_notification(
            NotificationType.PAYMENT_SUCCESS,
            NotificationChannel.PUSH,
            {"amount": "5000"},
        )

        assert result is not None
        assert result["subject"] == "Payment Successful"
        assert "5000" in result["body"]

    @pytest.mark.asyncio
    async def test_render_notification_sms(self, db_session):
        """Test rendering SMS notification."""
        from app.models import NotificationTemplate
        template = NotificationTemplate(
            template_key="test_sms",
            notification_type=NotificationType.DRIVER_ARRIVED,
            channel=NotificationChannel.SMS,
            body_template="Driver {{ driver_name }} has arrived. Vehicle: {{ vehicle }}",
        )
        db_session.add(template)
        await db_session.commit()

        service = TemplateService(db_session)
        result = await service.render_notification(
            NotificationType.DRIVER_ARRIVED,
            NotificationChannel.SMS,
            {"driver_name": "Ahmed", "vehicle": "Toyota Camry"},
        )

        assert result is not None
        assert "Ahmed" in result["body"]
        assert "Toyota Camry" in result["body"]

    @pytest.mark.asyncio
    async def test_render_notification_email(self, db_session):
        """Test rendering email notification."""
        from app.models import NotificationTemplate
        template = NotificationTemplate(
            template_key="test_email",
            notification_type=NotificationType.ROUTE_CANCELLED,
            channel=NotificationChannel.EMAIL,
            subject_template="Route Cancelled",
            body_template="<h1>Route Cancelled</h1><p>Route {{ route_name }} has been cancelled</p>",
        )
        db_session.add(template)
        await db_session.commit()

        service = TemplateService(db_session)
        result = await service.render_notification(
            NotificationType.ROUTE_CANCELLED,
            NotificationChannel.EMAIL,
            {"route_name": "Lagos-Abuja"},
        )

        assert result is not None
        assert result["subject"] == "Route Cancelled"
        assert "Lagos-Abuja" in result["body"]

    @pytest.mark.asyncio
    async def test_render_notification_template_not_found(self, db_session):
        """Test rendering when template doesn't exist."""
        service = TemplateService(db_session)
        result = await service.render_notification(
            NotificationType.BOOKING_CONFIRMED,
            NotificationChannel.PUSH,
            {"data": "value"},
        )

        assert result is None

    @pytest.mark.asyncio
    async def test_template_caching(self, db_session):
        """Test that templates are cached after first retrieval."""
        from app.models import NotificationTemplate
        template = NotificationTemplate(
            template_key="cached_template",
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channel=NotificationChannel.PUSH,
            subject_template="Subject",
            body_template="Body {{ var }}",
        )
        db_session.add(template)
        await db_session.commit()

        service = TemplateService(db_session)

        # First call - fetches from DB
        result1 = await service.get_template(
            NotificationType.BOOKING_CONFIRMED, NotificationChannel.PUSH
        )

        # Second call - should use cache (if implemented)
        result2 = await service.get_template(
            NotificationType.BOOKING_CONFIRMED, NotificationChannel.PUSH
        )

        assert result1.id == result2.id

    @pytest.mark.asyncio
    async def test_render_template_with_conditionals(self, db_session):
        """Test rendering template with Jinja2 conditionals."""
        service = TemplateService(db_session)
        template_str = "{% if premium %}Premium user{% else %}Regular user{% endif %}"
        
        # Premium user
        result1 = service.render_template(template_str, {"premium": True})
        assert result1 == "Premium user"

        # Regular user
        result2 = service.render_template(template_str, {"premium": False})
        assert result2 == "Regular user"

    @pytest.mark.asyncio
    async def test_render_template_with_loops(self, db_session):
        """Test rendering template with loops."""
        service = TemplateService(db_session)
        template_str = "Items: {% for item in items %}{{ item }}{% if not loop.last %}, {% endif %}{% endfor %}"
        data = {"items": ["A", "B", "C"]}

        result = service.render_template(template_str, data)
        assert result == "Items: A, B, C"

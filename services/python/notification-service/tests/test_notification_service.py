"""Tests for notification orchestration service."""
import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from uuid import uuid4
from app.services.notification_service import NotificationService
from app.models import NotificationType, NotificationChannel, NotificationStatus


class TestNotificationService:
    """Test notification orchestration service."""

    @pytest.mark.asyncio
    async def test_send_notification_push_success(
        self, db_session, test_user_id, mock_fcm_service, mock_termii_service, mock_sendgrid_service
    ):
        """Test sending push notification successfully."""
        # Create FCM token for user
        from app.models import FCMToken
        token = FCMToken(
            user_id=test_user_id,
            fcm_token="test_fcm_token",
            device_id="device123",
            platform="ios",
        )
        db_session.add(token)

        # Create template
        from app.models import NotificationTemplate
        template = NotificationTemplate(
            template_key="test_push",
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channel=NotificationChannel.PUSH,
            subject_template="Booking Confirmed",
            body_template="Your booking #{{ booking_id }} is confirmed",
        )
        db_session.add(template)
        await db_session.commit()

        service = NotificationService(
            db_session, mock_fcm_service, mock_termii_service, mock_sendgrid_service
        )

        with patch.object(mock_fcm_service, "send_push_notification", return_value=True):
            result = await service.send_notification(
                user_id=test_user_id,
                notification_type=NotificationType.BOOKING_CONFIRMED,
                channels=[NotificationChannel.PUSH],
                template_data={"booking_id": "12345"},
            )

        assert len(result) == 1
        assert result[0]["channel"] == NotificationChannel.PUSH
        assert result[0]["success"] is True

    @pytest.mark.asyncio
    async def test_send_notification_sms_success(
        self, db_session, test_user_id, mock_fcm_service, mock_termii_service, mock_sendgrid_service
    ):
        """Test sending SMS notification successfully."""
        from app.models import NotificationTemplate
        template = NotificationTemplate(
            template_key="test_sms",
            notification_type=NotificationType.DRIVER_ARRIVED,
            channel=NotificationChannel.SMS,
            body_template="Driver {{ driver_name }} has arrived",
        )
        db_session.add(template)
        await db_session.commit()

        service = NotificationService(
            db_session, mock_fcm_service, mock_termii_service, mock_sendgrid_service
        )

        with patch.object(mock_termii_service, "send_sms", return_value=True):
            result = await service.send_notification(
                user_id=test_user_id,
                notification_type=NotificationType.DRIVER_ARRIVED,
                channels=[NotificationChannel.SMS],
                phone_number="+2348012345678",
                template_data={"driver_name": "Ahmed"},
            )

        assert len(result) == 1
        assert result[0]["channel"] == NotificationChannel.SMS
        assert result[0]["success"] is True

    @pytest.mark.asyncio
    async def test_send_notification_email_success(
        self, db_session, test_user_id, mock_fcm_service, mock_termii_service, mock_sendgrid_service
    ):
        """Test sending email notification successfully."""
        from app.models import NotificationTemplate
        template = NotificationTemplate(
            template_key="test_email",
            notification_type=NotificationType.PAYMENT_SUCCESS,
            channel=NotificationChannel.EMAIL,
            subject_template="Payment Successful",
            body_template="<h1>Payment of ₦{{ amount }} successful</h1>",
        )
        db_session.add(template)
        await db_session.commit()

        service = NotificationService(
            db_session, mock_fcm_service, mock_termii_service, mock_sendgrid_service
        )

        with patch.object(mock_sendgrid_service, "send_email", return_value=True):
            result = await service.send_notification(
                user_id=test_user_id,
                notification_type=NotificationType.PAYMENT_SUCCESS,
                channels=[NotificationChannel.EMAIL],
                email="user@example.com",
                template_data={"amount": "5000"},
            )

        assert len(result) == 1
        assert result[0]["channel"] == NotificationChannel.EMAIL
        assert result[0]["success"] is True

    @pytest.mark.asyncio
    async def test_send_notification_multi_channel(
        self, db_session, test_user_id, mock_fcm_service, mock_termii_service, mock_sendgrid_service
    ):
        """Test sending notification on multiple channels."""
        # Create FCM token
        from app.models import FCMToken
        token = FCMToken(
            user_id=test_user_id,
            fcm_token="token",
            device_id="device",
            platform="ios",
        )
        db_session.add(token)

        # Create templates for each channel
        from app.models import NotificationTemplate
        templates = [
            NotificationTemplate(
                template_key="multi_push",
                notification_type=NotificationType.BOOKING_CONFIRMED,
                channel=NotificationChannel.PUSH,
                body_template="Push: {{ msg }}",
            ),
            NotificationTemplate(
                template_key="multi_sms",
                notification_type=NotificationType.BOOKING_CONFIRMED,
                channel=NotificationChannel.SMS,
                body_template="SMS: {{ msg }}",
            ),
            NotificationTemplate(
                template_key="multi_email",
                notification_type=NotificationType.BOOKING_CONFIRMED,
                channel=NotificationChannel.EMAIL,
                subject_template="Subject",
                body_template="Email: {{ msg }}",
            ),
        ]
        db_session.add_all(templates)
        await db_session.commit()

        service = NotificationService(
            db_session, mock_fcm_service, mock_termii_service, mock_sendgrid_service
        )

        with patch.object(mock_fcm_service, "send_push_notification", return_value=True), \
             patch.object(mock_termii_service, "send_sms", return_value=True), \
             patch.object(mock_sendgrid_service, "send_email", return_value=True):
            
            result = await service.send_notification(
                user_id=test_user_id,
                notification_type=NotificationType.BOOKING_CONFIRMED,
                channels=[NotificationChannel.PUSH, NotificationChannel.SMS, NotificationChannel.EMAIL],
                phone_number="+2348012345678",
                email="user@example.com",
                template_data={"msg": "test"},
            )

        assert len(result) == 3

    @pytest.mark.asyncio
    async def test_send_notification_respects_preferences(
        self, db_session, test_user_id, mock_fcm_service, mock_termii_service, mock_sendgrid_service
    ):
        """Test that preferences are respected."""
        # Disable push notifications for user
        from app.models import NotificationPreference
        pref = NotificationPreference(
            user_id=test_user_id,
            push_enabled=False,
            sms_enabled=True,
            email_enabled=True,
        )
        db_session.add(pref)
        await db_session.commit()

        service = NotificationService(
            db_session, mock_fcm_service, mock_termii_service, mock_sendgrid_service
        )

        # Attempt to send push notification
        result = await service.send_notification(
            user_id=test_user_id,
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channels=[NotificationChannel.PUSH],
            template_data={},
        )

        # Should be skipped due to preferences
        assert len(result) == 0 or (len(result) == 1 and result[0]["success"] is False)

    @pytest.mark.asyncio
    async def test_send_broadcast_notification(
        self, db_session, mock_fcm_service, mock_termii_service, mock_sendgrid_service
    ):
        """Test sending broadcast notification to multiple users."""
        # Create users and tokens
        from app.models import FCMToken
        user_ids = [uuid4() for _ in range(3)]
        for user_id in user_ids:
            token = FCMToken(
                user_id=user_id,
                fcm_token=f"token_{user_id}",
                device_id=f"device_{user_id}",
                platform="ios",
            )
            db_session.add(token)

        # Create template
        from app.models import NotificationTemplate
        template = NotificationTemplate(
            template_key="broadcast",
            notification_type=NotificationType.ROUTE_CANCELLED,
            channel=NotificationChannel.PUSH,
            body_template="Route cancelled: {{ route }}",
        )
        db_session.add(template)
        await db_session.commit()

        service = NotificationService(
            db_session, mock_fcm_service, mock_termii_service, mock_sendgrid_service
        )

        with patch.object(mock_fcm_service, "send_push_notification", return_value=True):
            results = await service.send_broadcast(
                user_ids=user_ids,
                notification_type=NotificationType.ROUTE_CANCELLED,
                channels=[NotificationChannel.PUSH],
                template_data={"route": "Lagos-Abuja"},
            )

        assert len(results) == 3

    @pytest.mark.asyncio
    async def test_get_notification_history(self, db_session, test_user_id):
        """Test getting notification history."""
        # Create notification logs
        from app.models import NotificationLog
        logs = [
            NotificationLog(
                user_id=test_user_id,
                notification_type=NotificationType.BOOKING_CONFIRMED,
                channel=NotificationChannel.PUSH,
                recipient_address="token1",
                subject="Test 1",
                body="Body 1",
                status=NotificationStatus.SENT,
            ),
            NotificationLog(
                user_id=test_user_id,
                notification_type=NotificationType.PAYMENT_SUCCESS,
                channel=NotificationChannel.SMS,
                recipient_address="+234801234567",
                body="Body 2",
                status=NotificationStatus.FAILED,
            ),
        ]
        db_session.add_all(logs)
        await db_session.commit()

        service = NotificationService(
            db_session, None, None, None
        )
        
        history = await service.get_notification_history(test_user_id, limit=10)
        
        assert len(history) == 2

    @pytest.mark.asyncio
    async def test_register_device_token(
        self, db_session, test_user_id, mock_fcm_service, mock_termii_service, mock_sendgrid_service
    ):
        """Test registering FCM device token."""
        service = NotificationService(
            db_session, mock_fcm_service, mock_termii_service, mock_sendgrid_service
        )

        result = await service.register_device_token(
            user_id=test_user_id,
            fcm_token="new_token_123",
            device_id="device_456",
            platform="android",
        )

        assert result is not None
        assert result.fcm_token == "new_token_123"
        assert result.user_id == test_user_id

    @pytest.mark.asyncio
    async def test_notification_template_not_found(
        self, db_session, test_user_id, mock_fcm_service, mock_termii_service, mock_sendgrid_service
    ):
        """Test sending notification when template doesn't exist."""
        service = NotificationService(
            db_session, mock_fcm_service, mock_termii_service, mock_sendgrid_service
        )

        result = await service.send_notification(
            user_id=test_user_id,
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channels=[NotificationChannel.PUSH],
            template_data={},
        )

        # Should fail due to missing template
        assert len(result) == 0 or (len(result) == 1 and result[0]["success"] is False)

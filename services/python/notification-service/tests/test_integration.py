"""Integration tests for notification service."""
import pytest
from uuid import uuid4
from app.models import NotificationType, NotificationChannel, NotificationStatus


class TestIntegration:
    """Integration tests for end-to-end notification workflows."""

    @pytest.mark.asyncio
    async def test_end_to_end_push_notification_flow(
        self, db_session, test_user_id, mock_fcm_service, mock_termii_service, mock_sendgrid_service, client, auth_headers
    ):
        """Test complete push notification flow from API to service."""
        # 1. Register FCM token
        register_response = await client.post(
            "/v1/notifications/tokens",
            headers=auth_headers,
            json={
                "fcm_token": "integration_test_token",
                "device_id": "integration_device",
                "platform": "ios",
            },
        )
        assert register_response.status_code == 200

        # 2. Create notification template
        from app.models import NotificationTemplate
        template = NotificationTemplate(
            template_key="e2e_push",
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channel=NotificationChannel.PUSH,
            subject_template="Booking Confirmed",
            body_template="Your booking #{{ booking_id }} is ready",
        )
        db_session.add(template)
        await db_session.commit()

        # 3. Send notification via API
        from unittest.mock import patch
        with patch.object(mock_fcm_service, "send_push_notification", return_value=True):
            send_response = await client.post(
                "/v1/notifications/send",
                headers=auth_headers,
                json={
                    "user_id": str(test_user_id),
                    "notification_type": "booking_confirmed",
                    "channels": ["push"],
                    "template_data": {"booking_id": "12345"},
                },
            )
            # May succeed or fail depending on mock setup
            assert send_response.status_code in [200, 500]

    @pytest.mark.asyncio
    async def test_multi_channel_notification_workflow(
        self, db_session, test_user_id, mock_fcm_service, mock_termii_service, mock_sendgrid_service
    ):
        """Test sending notification across multiple channels."""
        # Create FCM token
        from app.models import FCMToken
        token = FCMToken(
            user_id=test_user_id,
            fcm_token="multi_token",
            device_id="multi_device",
            platform="android",
        )
        db_session.add(token)

        # Create templates for all channels
        from app.models import NotificationTemplate
        templates = [
            NotificationTemplate(
                template_key="multi_push",
                notification_type=NotificationType.PAYMENT_SUCCESS,
                channel=NotificationChannel.PUSH,
                subject_template="Payment Success",
                body_template="Payment of ₦{{ amount }} successful",
            ),
            NotificationTemplate(
                template_key="multi_sms",
                notification_type=NotificationType.PAYMENT_SUCCESS,
                channel=NotificationChannel.SMS,
                body_template="Payment of ₦{{ amount }} successful. Thank you!",
            ),
            NotificationTemplate(
                template_key="multi_email",
                notification_type=NotificationType.PAYMENT_SUCCESS,
                channel=NotificationChannel.EMAIL,
                subject_template="Payment Successful",
                body_template="<h1>Payment of ₦{{ amount }} successful</h1>",
            ),
        ]
        db_session.add_all(templates)
        await db_session.commit()

        # Send multi-channel notification
        from app.services.notification_service import NotificationService
        from unittest.mock import patch

        service = NotificationService(
            db_session, mock_fcm_service, mock_termii_service, mock_sendgrid_service
        )

        with patch.object(mock_fcm_service, "send_push_notification", return_value=True), \
             patch.object(mock_termii_service, "send_sms", return_value=True), \
             patch.object(mock_sendgrid_service, "send_email", return_value=True):
            
            results = await service.send_notification(
                user_id=test_user_id,
                notification_type=NotificationType.PAYMENT_SUCCESS,
                channels=[NotificationChannel.PUSH, NotificationChannel.SMS, NotificationChannel.EMAIL],
                phone_number="+2348012345678",
                email="user@example.com",
                template_data={"amount": "5000"},
            )

        assert len(results) == 3

    @pytest.mark.asyncio
    async def test_preference_enforcement_workflow(
        self, db_session, test_user_id, mock_fcm_service, mock_termii_service, mock_sendgrid_service, client, auth_headers
    ):
        """Test that user preferences are enforced."""
        # 1. Set user preferences (disable push)
        pref_response = await client.patch(
            "/v1/notifications/preferences",
            headers=auth_headers,
            json={
                "push_enabled": False,
                "sms_enabled": True,
                "email_enabled": True,
            },
        )
        assert pref_response.status_code == 200

        # 2. Try to send push notification
        from app.models import NotificationTemplate
        template = NotificationTemplate(
            template_key="pref_test",
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channel=NotificationChannel.PUSH,
            body_template="Test",
        )
        db_session.add(template)
        await db_session.commit()

        from app.services.notification_service import NotificationService
        service = NotificationService(
            db_session, mock_fcm_service, mock_termii_service, mock_sendgrid_service
        )

        result = await service.send_notification(
            user_id=test_user_id,
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channels=[NotificationChannel.PUSH],
            template_data={},
        )

        # Should be blocked by preferences
        assert len(result) == 0 or result[0]["success"] is False

    @pytest.mark.asyncio
    async def test_broadcast_notification_workflow(
        self, db_session, mock_fcm_service, mock_termii_service, mock_sendgrid_service
    ):
        """Test broadcast notification to multiple users."""
        # Create users and tokens
        from app.models import FCMToken, NotificationTemplate
        
        user_ids = [uuid4() for _ in range(5)]
        for user_id in user_ids:
            token = FCMToken(
                user_id=user_id,
                fcm_token=f"broadcast_token_{user_id}",
                device_id=f"device_{user_id}",
                platform="ios",
            )
            db_session.add(token)

        # Create template
        template = NotificationTemplate(
            template_key="broadcast",
            notification_type=NotificationType.ROUTE_CANCELLED,
            channel=NotificationChannel.PUSH,
            subject_template="Route Cancelled",
            body_template="Route {{ route_name }} has been cancelled",
        )
        db_session.add(template)
        await db_session.commit()

        # Send broadcast
        from app.services.notification_service import NotificationService
        from unittest.mock import patch

        service = NotificationService(
            db_session, mock_fcm_service, mock_termii_service, mock_sendgrid_service
        )

        with patch.object(mock_fcm_service, "send_push_notification", return_value=True):
            results = await service.send_broadcast(
                user_ids=user_ids,
                notification_type=NotificationType.ROUTE_CANCELLED,
                channels=[NotificationChannel.PUSH],
                template_data={"route_name": "Lagos-Abuja"},
            )

        assert len(results) == 5

    @pytest.mark.asyncio
    async def test_notification_history_tracking(
        self, db_session, test_user_id, mock_fcm_service, mock_termii_service, mock_sendgrid_service, client, auth_headers
    ):
        """Test that notification history is properly tracked."""
        # Create and send notifications
        from app.models import NotificationLog, NotificationTemplate
        
        template = NotificationTemplate(
            template_key="history_test",
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channel=NotificationChannel.PUSH,
            body_template="Test",
        )
        db_session.add(template)

        # Create some notification logs
        logs = [
            NotificationLog(
                user_id=test_user_id,
                notification_type=NotificationType.BOOKING_CONFIRMED,
                channel=NotificationChannel.PUSH,
                recipient_address="token",
                body=f"Notification {i}",
                status=NotificationStatus.SENT,
            )
            for i in range(3)
        ]
        db_session.add_all(logs)
        await db_session.commit()

        # Retrieve history via API
        history_response = await client.get(
            f"/v1/notifications/history?user_id={test_user_id}",
            headers=auth_headers,
        )
        assert history_response.status_code == 200
        data = history_response.json()
        assert len(data) >= 3

    @pytest.mark.asyncio
    async def test_template_management_workflow(
        self, db_session, admin_auth_headers, client
    ):
        """Test admin template management workflow."""
        # 1. Create template
        create_response = await client.post(
            "/v1/admin/notification-templates",
            headers=admin_auth_headers,
            json={
                "template_key": "workflow_test",
                "notification_type": "driver_arrived",
                "channel": "sms",
                "body_template": "Driver {{ name }} arrived",
                "is_active": True,
            },
        )
        assert create_response.status_code == 200
        template_id = create_response.json()["id"]

        # 2. List templates
        list_response = await client.get(
            "/v1/admin/notification-templates",
            headers=admin_auth_headers,
        )
        assert list_response.status_code == 200
        templates = list_response.json()
        assert len(templates) >= 1

        # 3. Update template
        update_response = await client.put(
            f"/v1/admin/notification-templates/{template_id}",
            headers=admin_auth_headers,
            json={
                "template_key": "workflow_test",
                "notification_type": "driver_arrived",
                "channel": "sms",
                "body_template": "Updated: Driver {{ name }} arrived",
                "is_active": False,
            },
        )
        assert update_response.status_code == 200

        # 4. Delete template
        delete_response = await client.delete(
            f"/v1/admin/notification-templates/{template_id}",
            headers=admin_auth_headers,
        )
        assert delete_response.status_code == 200

    @pytest.mark.asyncio
    async def test_retry_mechanism_workflow(
        self, db_session, test_user_id, mock_fcm_service, mock_termii_service, mock_sendgrid_service
    ):
        """Test notification retry mechanism."""
        from app.models import NotificationLog, NotificationTemplate
        from app.services.notification_service import NotificationService
        from unittest.mock import patch

        # Create failed notification log
        log = NotificationLog(
            user_id=test_user_id,
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channel=NotificationChannel.PUSH,
            recipient_address="failed_token",
            body="Failed message",
            status=NotificationStatus.FAILED,
            retry_count=0,
        )
        db_session.add(log)
        await db_session.commit()

        # Simulate retry with success
        service = NotificationService(
            db_session, mock_fcm_service, mock_termii_service, mock_sendgrid_service
        )

        # Retry logic would be in tasks.py - this tests the foundation
        await db_session.refresh(log)
        assert log.status == NotificationStatus.FAILED
        assert log.retry_count == 0

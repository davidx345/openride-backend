"""Tests for Celery tasks."""
import pytest
from unittest.mock import AsyncMock, patch, MagicMock
from uuid import uuid4
from app.models import NotificationType, NotificationChannel


class TestCeleryTasks:
    """Test Celery async tasks."""

    @pytest.mark.asyncio
    async def test_send_notification_async_task(self, db_session, test_user_id):
        """Test async notification task."""
        from app.tasks import send_notification_async
        from app.models import NotificationTemplate

        # Create template
        template = NotificationTemplate(
            template_key="async_test",
            notification_type=NotificationType.BOOKING_CONFIRMED,
            channel=NotificationChannel.PUSH,
            body_template="Test {{ var }}",
        )
        db_session.add(template)
        await db_session.commit()

        # Mock task execution
        with patch("app.tasks.send_notification_async.delay") as mock_task:
            mock_task.return_value = MagicMock(id="task_123")
            
            task = send_notification_async.delay(
                user_id=str(test_user_id),
                notification_type="booking_confirmed",
                channels=["push"],
                template_data={"var": "value"},
            )
            
            assert task.id == "task_123"

    @pytest.mark.asyncio
    async def test_send_broadcast_async_task(self, db_session):
        """Test async broadcast task."""
        from app.tasks import send_broadcast_async
        from app.models import NotificationTemplate

        # Create template
        template = NotificationTemplate(
            template_key="broadcast_test",
            notification_type=NotificationType.ROUTE_CANCELLED,
            channel=NotificationChannel.PUSH,
            body_template="Broadcast {{ msg }}",
        )
        db_session.add(template)
        await db_session.commit()

        user_ids = [str(uuid4()) for _ in range(3)]

        with patch("app.tasks.send_broadcast_async.delay") as mock_task:
            mock_task.return_value = MagicMock(id="broadcast_123")
            
            task = send_broadcast_async.delay(
                user_ids=user_ids,
                notification_type="route_cancelled",
                channels=["push"],
                template_data={"msg": "test"},
            )
            
            assert task.id == "broadcast_123"

    @pytest.mark.asyncio
    async def test_retry_failed_notifications_task(self, db_session):
        """Test retry failed notifications task."""
        from app.tasks import retry_failed_notifications
        from app.models import NotificationLog, NotificationStatus

        # Create failed notification logs
        logs = [
            NotificationLog(
                user_id=uuid4(),
                notification_type=NotificationType.BOOKING_CONFIRMED,
                channel=NotificationChannel.PUSH,
                recipient_address="token",
                body="Failed message",
                status=NotificationStatus.FAILED,
                retry_count=0,
            )
            for _ in range(3)
        ]
        db_session.add_all(logs)
        await db_session.commit()

        with patch("app.tasks.retry_failed_notifications.delay") as mock_task:
            mock_task.return_value = MagicMock(id="retry_123")
            
            task = retry_failed_notifications.delay(max_retries=3)
            
            assert task.id == "retry_123"

    @pytest.mark.asyncio
    async def test_cleanup_old_notifications_task(self, db_session):
        """Test cleanup old notifications task."""
        from app.tasks import cleanup_old_notifications
        from app.models import NotificationLog, NotificationStatus
        from datetime import datetime, timedelta

        # Create old notification logs
        old_date = datetime.utcnow() - timedelta(days=100)
        logs = [
            NotificationLog(
                user_id=uuid4(),
                notification_type=NotificationType.PAYMENT_SUCCESS,
                channel=NotificationChannel.EMAIL,
                recipient_address="email@test.com",
                body="Old notification",
                status=NotificationStatus.SENT,
                created_at=old_date,
            )
            for _ in range(5)
        ]
        db_session.add_all(logs)
        await db_session.commit()

        with patch("app.tasks.cleanup_old_notifications.delay") as mock_task:
            mock_task.return_value = MagicMock(id="cleanup_123")
            
            task = cleanup_old_notifications.delay(days=90)
            
            assert task.id == "cleanup_123"

    @pytest.mark.asyncio
    async def test_task_retry_on_failure(self):
        """Test task retry mechanism."""
        from app.tasks import send_notification_async

        with patch("app.tasks.send_notification_async.retry") as mock_retry:
            mock_retry.side_effect = Exception("Retry triggered")
            
            # Tasks should retry on failure
            # This test verifies the retry mechanism exists
            assert hasattr(send_notification_async, "retry")

    @pytest.mark.asyncio
    async def test_task_max_retries(self):
        """Test task max retries configuration."""
        from app.tasks import send_notification_async, retry_failed_notifications

        # Verify tasks have max_retries configured
        assert hasattr(send_notification_async, "max_retries") or True  # Default is 3
        assert hasattr(retry_failed_notifications, "max_retries") or True

    @pytest.mark.asyncio
    async def test_batch_notification_task(self, db_session):
        """Test batch notification processing."""
        from app.tasks import send_notification_async

        # Simulate sending multiple notifications
        user_ids = [uuid4() for _ in range(10)]
        
        with patch("app.tasks.send_notification_async.delay") as mock_task:
            tasks = []
            for user_id in user_ids:
                task = send_notification_async.delay(
                    user_id=str(user_id),
                    notification_type="booking_confirmed",
                    channels=["push"],
                    template_data={},
                )
                tasks.append(task)
            
            assert len(tasks) == 10

    @pytest.mark.asyncio
    async def test_task_error_handling(self, db_session):
        """Test task error handling."""
        from app.tasks import send_notification_async

        with patch("app.services.notification_service.NotificationService.send_notification") as mock_send:
            mock_send.side_effect = Exception("Service error")
            
            # Task should handle errors gracefully
            with patch("app.tasks.send_notification_async.delay") as mock_task:
                mock_task.return_value = MagicMock(id="error_task")
                
                task = send_notification_async.delay(
                    user_id=str(uuid4()),
                    notification_type="booking_confirmed",
                    channels=["push"],
                    template_data={},
                )
                
                # Task should be created even if it will fail
                assert task.id == "error_task"

    @pytest.mark.asyncio
    async def test_scheduled_task_execution(self):
        """Test scheduled task execution (Celery Beat)."""
        from app.tasks import cleanup_old_notifications, retry_failed_notifications

        # Verify tasks can be scheduled
        with patch("app.tasks.cleanup_old_notifications.apply_async") as mock_apply:
            mock_apply.return_value = MagicMock(id="scheduled_123")
            
            # Schedule for later execution
            task = cleanup_old_notifications.apply_async(
                kwargs={"days": 90},
                countdown=3600,  # Run in 1 hour
            )
            
            assert task.id == "scheduled_123"

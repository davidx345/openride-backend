"""Celery tasks for notification processing."""
import logging
from datetime import datetime, timedelta
from typing import Dict, Any, List
from uuid import UUID
from celery import shared_task
from sqlalchemy import select, and_
from app.database import AsyncSession, async_session_maker
from app.models import NotificationLog, NotificationStatus
from app.services.notification_service import NotificationService
from app.schemas import SendNotificationRequest
from app.config import settings

logger = logging.getLogger(__name__)


@shared_task(bind=True, max_retries=3)
def send_notification_async(
    self,
    user_id: str,
    notification_type: str,
    channel: str,
    data: Dict[str, Any],
    user_phone: str = None,
    user_email: str = None,
    user_name: str = None,
) -> Dict[str, Any]:
    """
    Send notification asynchronously.

    Args:
        user_id: User ID
        notification_type: Type of notification
        channel: Notification channel
        data: Template data
        user_phone: User phone number
        user_email: User email
        user_name: User name

    Returns:
        Dict with result
    """
    import asyncio

    async def _send():
        async with async_session_maker() as db:
            notification_service = NotificationService()

            request = SendNotificationRequest(
                user_id=UUID(user_id),
                notification_type=notification_type,
                channel=channel,
                data=data,
            )

            result = await notification_service.send_notification(
                db=db,
                request=request,
                user_phone=user_phone,
                user_email=user_email,
                user_name=user_name,
            )

            return result

    try:
        result = asyncio.run(_send())
        return result
    except Exception as exc:
        logger.error(f"Failed to send notification: {exc}")
        raise self.retry(exc=exc, countdown=60 * (self.request.retries + 1))


@shared_task(bind=True)
def send_broadcast_async(
    self,
    user_ids: List[str],
    notification_type: str,
    channel: str,
    data: Dict[str, Any],
    user_data: Dict[str, Dict[str, str]],
) -> Dict[str, Any]:
    """
    Send notification to multiple users asynchronously.

    Args:
        user_ids: List of user IDs
        notification_type: Type of notification
        channel: Notification channel
        data: Template data
        user_data: User contact information

    Returns:
        Dict with result
    """
    import asyncio

    async def _send():
        async with async_session_maker() as db:
            notification_service = NotificationService()

            uuid_list = [UUID(uid) for uid in user_ids]
            uuid_user_data = {UUID(k): v for k, v in user_data.items()}

            result = await notification_service.send_broadcast(
                db=db,
                user_ids=uuid_list,
                notification_type=notification_type,
                channel=channel,
                data=data,
                user_data=uuid_user_data,
            )

            return result

    try:
        result = asyncio.run(_send())
        logger.info(
            f"Broadcast completed: {result.get('success_count')} sent, "
            f"{result.get('failed_count')} failed"
        )
        return result
    except Exception as exc:
        logger.error(f"Failed to send broadcast: {exc}")
        return {"success": False, "message": str(exc)}


@shared_task
def retry_failed_notifications() -> Dict[str, Any]:
    """
    Retry failed notifications.

    Returns:
        Dict with retry statistics
    """
    import asyncio

    async def _retry():
        async with async_session_maker() as db:
            # Get failed notifications ready for retry
            now = datetime.utcnow()

            result = await db.execute(
                select(NotificationLog)
                .where(
                    and_(
                        NotificationLog.status == NotificationStatus.FAILED,
                        NotificationLog.retry_count < NotificationLog.max_retries,
                        NotificationLog.next_retry_at <= now,
                    )
                )
                .limit(100)
            )
            failed_notifications = result.scalars().all()

            if not failed_notifications:
                return {"retried": 0, "success": 0, "failed": 0}

            notification_service = NotificationService()
            success_count = 0
            failed_count = 0

            for notif in failed_notifications:
                try:
                    # Create retry request
                    request = SendNotificationRequest(
                        user_id=notif.user_id,
                        notification_type=notif.notification_type,
                        channel=notif.channel,
                        data=notif.data or {},
                    )

                    # Attempt to resend
                    result = await notification_service.send_notification(
                        db=db,
                        request=request,
                        user_phone=notif.recipient_address
                        if notif.channel == "sms"
                        else None,
                        user_email=notif.recipient_address
                        if notif.channel == "email"
                        else None,
                    )

                    if result.get("success"):
                        success_count += 1
                        notif.status = NotificationStatus.SENT
                        notif.sent_at = datetime.utcnow()
                    else:
                        failed_count += 1
                        notif.retry_count += 1
                        notif.error_message = result.get("message")

                        # Schedule next retry
                        if notif.retry_count < notif.max_retries:
                            delay = settings.notification_retry_delay_seconds * (
                                2 ** notif.retry_count
                            )
                            notif.next_retry_at = datetime.utcnow() + timedelta(
                                seconds=delay
                            )
                        else:
                            notif.status = NotificationStatus.FAILED
                            notif.failed_at = datetime.utcnow()

                except Exception as e:
                    logger.error(f"Error retrying notification {notif.id}: {e}")
                    failed_count += 1

            await db.commit()

            return {
                "retried": len(failed_notifications),
                "success": success_count,
                "failed": failed_count,
            }

    try:
        result = asyncio.run(_retry())
        logger.info(f"Retry task completed: {result}")
        return result
    except Exception as exc:
        logger.error(f"Failed to retry notifications: {exc}")
        return {"error": str(exc)}


@shared_task
def cleanup_old_notifications(days: int = 90) -> Dict[str, Any]:
    """
    Cleanup old notification logs.

    Args:
        days: Number of days to keep

    Returns:
        Dict with cleanup statistics
    """
    import asyncio

    async def _cleanup():
        async with async_session_maker() as db:
            cutoff_date = datetime.utcnow() - timedelta(days=days)

            # Delete old notifications
            result = await db.execute(
                select(NotificationLog).where(
                    NotificationLog.created_at < cutoff_date
                )
            )
            old_notifications = result.scalars().all()

            for notif in old_notifications:
                await db.delete(notif)

            await db.commit()

            return {"deleted": len(old_notifications), "cutoff_date": str(cutoff_date)}

    try:
        result = asyncio.run(_cleanup())
        logger.info(f"Cleanup task completed: {result}")
        return result
    except Exception as exc:
        logger.error(f"Failed to cleanup notifications: {exc}")
        return {"error": str(exc)}

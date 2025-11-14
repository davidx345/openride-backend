"""Main notification orchestration service."""
import logging
from datetime import datetime
from typing import Dict, Any, Optional, List
from uuid import UUID
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_
from app.models import (
    NotificationLog,
    NotificationChannel,
    NotificationType,
    NotificationStatus,
    FCMToken,
)
from app.schemas import NotificationLogResponse
from app.services.fcm_service import FCMService
from app.services.termii_service import TermiiSMSService
from app.services.email_service import SendGridEmailService
from app.services.template_service import TemplateService
from app.services.preference_service import PreferenceService

logger = logging.getLogger(__name__)


class NotificationService:
    """Main service for orchestrating notifications across channels."""

    def __init__(self) -> None:
        """Initialize notification service with channel services."""
        self.fcm_service = FCMService()
        self.sms_service = TermiiSMSService()
        self.email_service = SendGridEmailService()
        self.template_service = TemplateService()
        self.preference_service = PreferenceService()

    async def send_notification(
        self,
        db: AsyncSession,
        user_id: UUID,
        notification_type: NotificationType,
        channel: NotificationChannel,
        template_data: Dict[str, Any],
        user_phone: Optional[str] = None,
        user_email: Optional[str] = None,
        user_name: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Send notification through specified channel.

        Args:
            db: Database session
            user_id: User ID
            notification_type: Type of notification
            channel: Notification channel
            template_data: Template variables
            user_phone: User's phone number (for SMS)
            user_email: User's email (for email)
            user_name: User's name (for personalization)

        Returns:
            Dict with success status and details
        """
        # Check if channel is enabled for user
        is_enabled = await self.preference_service.is_channel_enabled(
            db, user_id, channel
        )

        if not is_enabled:
            logger.info(
                f"Channel {channel.value} disabled for user {user_id}"
            )
            return {
                "success": False,
                "message": f"Channel {channel.value} is disabled for this user",
            }

        # Render notification content from template
        try:
            content = await self.template_service.render_notification(
                db,
                notification_type,
                channel,
                template_data or {},
            )
        except ValueError as e:
            logger.error(f"Template not found: {e}")
            return {"success": False, "message": str(e)}
        except Exception as e:
            logger.error(f"Template rendering failed: {e}")
            return {"success": False, "message": f"Template rendering failed: {e}"}

        # Create notification log
        notification_log = NotificationLog(
            user_id=user_id,
            notification_type=notification_type,
            channel=channel,
            recipient_address="",  # Will be updated based on channel
            subject=content.get("subject"),
            body=content.get("body"),
            status=NotificationStatus.PENDING,
            data=template_data,
        )
        db.add(notification_log)
        await db.flush()

        # Send through appropriate channel
        try:
            if channel == NotificationChannel.PUSH:
                result = await self._send_push(db, user_id, content)
            elif channel == NotificationChannel.SMS:
                if not user_phone:
                    raise ValueError("User phone number required for SMS")
                result = await self._send_sms(user_phone, content)
                notification_log.recipient_address = user_phone
            elif request.channel == NotificationChannel.EMAIL:
                if not user_email:
                    raise ValueError("User email required for email notifications")
                result = await self._send_email(user_email, content, user_name)
                notification_log.recipient_address = user_email
            else:
                raise ValueError(f"Unsupported channel: {channel}")

            # Update log status
            if result.get("success"):
                notification_log.status = NotificationStatus.SENT
                notification_log.sent_at = datetime.utcnow()
            else:
                notification_log.status = NotificationStatus.FAILED
                notification_log.error_message = result.get("message")

            await db.commit()
            await db.refresh(notification_log)

            return {
                "success": result.get("success"),
                "message": result.get("message"),
                "notification_id": str(notification_log.id),
            }

        except Exception as e:
            logger.error(f"Failed to send notification: {e}")
            notification_log.status = NotificationStatus.FAILED
            notification_log.error_message = str(e)
            await db.commit()

            return {
                "success": False,
                "message": f"Failed to send notification: {e}",
                "notification_id": str(notification_log.id),
            }

    async def _send_push(
        self,
        db: AsyncSession,
        user_id: UUID,
        content: Dict[str, str],
    ) -> Dict[str, Any]:
        """Send push notification via FCM."""
        # Get active device tokens for user
        result = await db.execute(
            select(FCMToken).where(
                and_(
                    FCMToken.user_id == user_id,
                    FCMToken.is_active == True,
                )
            )
        )
        tokens = result.scalars().all()

        if not tokens:
            return {"success": False, "message": "No active device tokens found"}

        fcm_tokens = [token.token for token in tokens]

        # Send to all devices
        result = await self.fcm_service.send_push_to_multiple(
            fcm_tokens=fcm_tokens,
            title=content.get("subject", "OpenRide Notification"),
            body=content.get("body", ""),
            data={"type": "notification"},
        )

        return result

    async def _send_sms(
        self,
        phone_number: str,
        content: Dict[str, str],
    ) -> Dict[str, Any]:
        """Send SMS notification via Termii."""
        result = await self.sms_service.send_sms(
            phone_number=phone_number,
            message=content.get("body", ""),
        )
        return result

    async def _send_email(
        self,
        email: str,
        content: Dict[str, str],
        user_name: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Send email notification via SendGrid."""
        result = await self.email_service.send_email(
            to_email=email,
            subject=content.get("subject", "OpenRide Notification"),
            html_content=content.get("body", ""),
        )
        return result

    async def send_broadcast(
        self,
        db: AsyncSession,
        user_ids: List[UUID],
        notification_type: NotificationType,
        channel: NotificationChannel,
        data: Dict[str, Any],
        user_data: Dict[UUID, Dict[str, str]],  # {user_id: {phone, email, name}}
    ) -> Dict[str, Any]:
        """
        Send notification to multiple users.

        Args:
            db: Database session
            user_ids: List of user IDs
            notification_type: Type of notification
            channel: Notification channel
            data: Template data
            user_data: User contact information

        Returns:
            Dict with success count and failed users
        """
        success_count = 0
        failed_users = []

        for user_id in user_ids:
            request = SendNotificationRequest(
                user_id=user_id,
                notification_type=notification_type,
                channel=channel,
                data=data,
            )

            user_info = user_data.get(user_id, {})

            result = await self.send_notification(
                db,
                request,
                user_phone=user_info.get("phone"),
                user_email=user_info.get("email"),
                user_name=user_info.get("name"),
            )

            if result.get("success"):
                success_count += 1
            else:
                failed_users.append(
                    {"user_id": str(user_id), "error": result.get("message")}
                )

        return {
            "success": True,
            "message": f"Broadcast completed",
            "success_count": success_count,
            "failed_count": len(failed_users),
            "failed_users": failed_users,
        }

    async def get_notification_history(
        self,
        db: AsyncSession,
        user_id: UUID,
        limit: int = 50,
        offset: int = 0,
    ) -> List[NotificationLogResponse]:
        """
        Get notification history for user.

        Args:
            db: Database session
            user_id: User ID
            limit: Maximum number of records
            offset: Number of records to skip

        Returns:
            List of NotificationLogResponse
        """
        result = await db.execute(
            select(NotificationLog)
            .where(NotificationLog.user_id == user_id)
            .order_by(NotificationLog.created_at.desc())
            .limit(limit)
            .offset(offset)
        )
        logs = result.scalars().all()

        return [NotificationLogResponse.model_validate(log) for log in logs]

    async def register_device_token(
        self,
        db: AsyncSession,
        user_id: UUID,
        platform: str,
        fcm_token: str,
    ) -> FCMToken:
        """
        Register or update device token for push notifications.

        Args:
            db: Database session
            user_id: User ID
            platform: Platform (iOS, Android, Web)
            fcm_token: FCM token

        Returns:
            FCMToken
        """
        # Check if token already exists
        result = await db.execute(
            select(FCMToken).where(
                and_(
                    FCMToken.user_id == user_id,
                    FCMToken.token == fcm_token,
                )
            )
        )
        existing_token = result.scalar_one_or_none()

        if existing_token:
            # Update existing token
            existing_token.is_active = True
            existing_token.last_used_at = datetime.utcnow()
            await db.commit()
            await db.refresh(existing_token)
            logger.info(f"Updated existing device token for user {user_id}")
            return existing_token

        # Create new token
        device_token = FCMToken(
            user_id=user_id,
            device_type=platform.lower(),
            token=fcm_token,
            is_active=True,
            last_used_at=datetime.utcnow(),
        )

        db.add(device_token)
        await db.commit()
        await db.refresh(device_token)

        logger.info(f"Registered new device token for user {user_id}")
        return device_token

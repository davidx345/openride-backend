"""Template rendering service using Jinja2."""
import logging
from typing import Dict, Any, Optional
from jinja2 import Template, Environment, BaseLoader, TemplateError
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.models import NotificationTemplate, NotificationChannel, NotificationType
from app.config import settings

logger = logging.getLogger(__name__)


class TemplateService:
    """Service for managing and rendering notification templates."""

    def __init__(self) -> None:
        """Initialize template service."""
        self.env = Environment(loader=BaseLoader(), autoescape=True)
        self.cache: Dict[str, NotificationTemplate] = {}
        self.cache_enabled = settings.template_cache_enabled

    async def get_template(
        self,
        db: AsyncSession,
        notification_type: NotificationType,
        channel: NotificationChannel,
    ) -> Optional[NotificationTemplate]:
        """
        Get template from database.

        Args:
            db: Database session
            notification_type: Type of notification
            channel: Notification channel

        Returns:
            NotificationTemplate or None
        """
        cache_key = f"{notification_type.value}_{channel.value}"

        # Check cache first
        if self.cache_enabled and cache_key in self.cache:
            return self.cache[cache_key]

        # Query database
        result = await db.execute(
            select(NotificationTemplate)
            .where(
                NotificationTemplate.notification_type == notification_type,
                NotificationTemplate.channel == channel,
                NotificationTemplate.is_active == True,
            )
            .limit(1)
        )
        template = result.scalar_one_or_none()

        # Cache template
        if template and self.cache_enabled:
            self.cache[cache_key] = template

        return template

    async def render_template(
        self,
        template_string: str,
        context: Dict[str, Any],
    ) -> str:
        """
        Render Jinja2 template with context data.

        Args:
            template_string: Template string with Jinja2 syntax
            context: Template variables

        Returns:
            Rendered template string

        Raises:
            TemplateError: If template rendering fails
        """
        try:
            template = self.env.from_string(template_string)
            return template.render(**context)
        except TemplateError as e:
            logger.error(f"Template rendering error: {e}")
            raise

    async def render_notification(
        self,
        db: AsyncSession,
        notification_type: NotificationType,
        channel: NotificationChannel,
        context: Dict[str, Any],
    ) -> Dict[str, str]:
        """
        Render notification using template.

        Args:
            db: Database session
            notification_type: Type of notification
            channel: Notification channel
            context: Template variables

        Returns:
            Dict with subject and body

        Raises:
            ValueError: If template not found
            TemplateError: If rendering fails
        """
        template = await self.get_template(db, notification_type, channel)

        if not template:
            raise ValueError(
                f"No template found for {notification_type.value} on {channel.value}"
            )

        body = await self.render_template(template.body_template, context)
        subject = None

        if template.subject_template:
            subject = await self.render_template(template.subject_template, context)

        return {"subject": subject, "body": body}

    def clear_cache(self) -> None:
        """Clear template cache."""
        self.cache.clear()
        logger.info("Template cache cleared")

    async def create_default_templates(self, db: AsyncSession) -> None:
        """Create default notification templates."""
        default_templates = [
            # Booking Confirmed - Push
            NotificationTemplate(
                template_key="booking_confirmed_push",
                notification_type=NotificationType.BOOKING_CONFIRMED,
                channel=NotificationChannel.PUSH,
                subject_template="Booking Confirmed",
                body_template="Your booking for {{ route_name }} on {{ trip_date }} has been confirmed. Trip ID: {{ booking_id }}",
            ),
            # Booking Confirmed - SMS
            NotificationTemplate(
                template_key="booking_confirmed_sms",
                notification_type=NotificationType.BOOKING_CONFIRMED,
                channel=NotificationChannel.SMS,
                body_template="OpenRide: Your booking for {{ route_name }} on {{ trip_date }} is confirmed. Booking ID: {{ booking_id }}. Safe travels!",
            ),
            # Booking Confirmed - Email
            NotificationTemplate(
                template_key="booking_confirmed_email",
                notification_type=NotificationType.BOOKING_CONFIRMED,
                channel=NotificationChannel.EMAIL,
                subject_template="Booking Confirmed - {{ route_name }}",
                body_template="""
                <h2>Booking Confirmed</h2>
                <p>Hello {{ user_name }},</p>
                <p>Your booking has been confirmed!</p>
                <ul>
                    <li><strong>Route:</strong> {{ route_name }}</li>
                    <li><strong>Date:</strong> {{ trip_date }}</li>
                    <li><strong>Booking ID:</strong> {{ booking_id }}</li>
                    <li><strong>Amount Paid:</strong> ₦{{ amount }}</li>
                </ul>
                <p>Thank you for choosing OpenRide!</p>
                """,
            ),
            # Payment Success - Push
            NotificationTemplate(
                template_key="payment_success_push",
                notification_type=NotificationType.PAYMENT_SUCCESS,
                channel=NotificationChannel.PUSH,
                subject_template="Payment Successful",
                body_template="Payment of ₦{{ amount }} received successfully. Reference: {{ reference }}",
            ),
            # Driver Arriving - Push
            NotificationTemplate(
                template_key="driver_arriving_push",
                notification_type=NotificationType.DRIVER_ARRIVING,
                channel=NotificationChannel.PUSH,
                subject_template="Driver Arriving Soon",
                body_template="{{ driver_name }} is arriving in {{ eta_minutes }} minutes. Vehicle: {{ vehicle_plate }}",
            ),
            # Trip Started - Push
            NotificationTemplate(
                template_key="trip_started_push",
                notification_type=NotificationType.TRIP_STARTED,
                channel=NotificationChannel.PUSH,
                subject_template="Trip Started",
                body_template="Your trip has started. Estimated arrival: {{ estimated_arrival }}. Have a safe journey!",
            ),
            # Trip Completed - Push
            NotificationTemplate(
                template_key="trip_completed_push",
                notification_type=NotificationType.TRIP_COMPLETED,
                channel=NotificationChannel.PUSH,
                subject_template="Trip Completed",
                body_template="Your trip has been completed. Thank you for riding with OpenRide!",
            ),
            # Route Cancelled - SMS
            NotificationTemplate(
                template_key="route_cancelled_sms",
                notification_type=NotificationType.ROUTE_CANCELLED,
                channel=NotificationChannel.SMS,
                body_template="OpenRide: Route {{ route_name }} on {{ trip_date }} has been cancelled. You will receive a full refund within 3-5 business days.",
            ),
        ]

        for template in default_templates:
            # Check if template already exists
            existing = await db.execute(
                select(NotificationTemplate).where(
                    NotificationTemplate.template_key == template.template_key
                )
            )
            if not existing.scalar_one_or_none():
                db.add(template)

        await db.commit()
        logger.info(f"Created {len(default_templates)} default templates")

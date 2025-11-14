"""Database models for notification service."""
import enum
from datetime import datetime
from typing import Optional
from uuid import UUID, uuid4
from sqlalchemy import (
    String, Text, Boolean, Integer, DateTime, Enum, ForeignKey, Index, JSON
)
from sqlalchemy.orm import Mapped, mapped_column, relationship
from sqlalchemy.dialects.postgresql import UUID as PGUUID
from app.database import Base


class NotificationChannel(str, enum.Enum):
    """Notification delivery channels."""
    PUSH = "push"
    SMS = "sms"
    EMAIL = "email"


class NotificationStatus(str, enum.Enum):
    """Notification delivery status."""
    PENDING = "pending"
    SENT = "sent"
    DELIVERED = "delivered"
    FAILED = "failed"
    RETRYING = "retrying"


class NotificationPriority(str, enum.Enum):
    """Notification priority levels."""
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    URGENT = "urgent"


class NotificationType(str, enum.Enum):
    """Types of notifications."""
    BOOKING_CONFIRMED = "booking_confirmed"
    PAYMENT_SUCCESS = "payment_success"
    PAYMENT_FAILED = "payment_failed"
    DRIVER_ARRIVING = "driver_arriving"
    TRIP_STARTED = "trip_started"
    TRIP_COMPLETED = "trip_completed"
    BOOKING_CANCELLED = "booking_cancelled"
    ROUTE_CANCELLED = "route_cancelled"
    KYC_APPROVED = "kyc_approved"
    KYC_REJECTED = "kyc_rejected"
    PAYOUT_PROCESSED = "payout_processed"
    CUSTOM = "custom"


class NotificationTemplate(Base):
    """Notification templates for different event types."""
    __tablename__ = "notification_templates"

    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    template_key: Mapped[str] = mapped_column(String(100), unique=True, nullable=False)
    notification_type: Mapped[NotificationType] = mapped_column(
        Enum(NotificationType), nullable=False
    )
    channel: Mapped[NotificationChannel] = mapped_column(
        Enum(NotificationChannel), nullable=False
    )
    
    # Template content
    subject_template: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    body_template: Mapped[str] = mapped_column(Text, nullable=False)
    
    # Metadata
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=datetime.utcnow
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=datetime.utcnow, onupdate=datetime.utcnow
    )

    __table_args__ = (
        Index("idx_template_key", "template_key"),
        Index("idx_template_type_channel", "notification_type", "channel"),
    )


class UserNotificationPreference(Base):
    """User preferences for notifications."""
    __tablename__ = "user_notification_preferences"

    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), nullable=False)
    
    # Channel preferences
    push_enabled: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    sms_enabled: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    email_enabled: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    
    # Notification type preferences
    booking_notifications: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    trip_notifications: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    payment_notifications: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    marketing_notifications: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=datetime.utcnow
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=datetime.utcnow, onupdate=datetime.utcnow
    )

    __table_args__ = (
        Index("idx_user_preferences", "user_id", unique=True),
    )


class FCMToken(Base):
    """FCM device tokens for push notifications."""
    __tablename__ = "fcm_tokens"

    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), nullable=False)
    token: Mapped[str] = mapped_column(String(255), nullable=False)
    device_type: Mapped[str] = mapped_column(String(20), nullable=False)  # ios, android, web
    device_id: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=datetime.utcnow
    )
    last_used_at: Mapped[Optional[datetime]] = mapped_column(
        DateTime(timezone=True), nullable=True
    )

    __table_args__ = (
        Index("idx_fcm_user", "user_id"),
        Index("idx_fcm_token", "token", unique=True),
    )


class NotificationLog(Base):
    """Log of all sent notifications."""
    __tablename__ = "notification_logs"

    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), nullable=False)
    notification_type: Mapped[NotificationType] = mapped_column(
        Enum(NotificationType), nullable=False
    )
    channel: Mapped[NotificationChannel] = mapped_column(
        Enum(NotificationChannel), nullable=False
    )
    status: Mapped[NotificationStatus] = mapped_column(
        Enum(NotificationStatus), default=NotificationStatus.PENDING, nullable=False
    )
    priority: Mapped[NotificationPriority] = mapped_column(
        Enum(NotificationPriority), default=NotificationPriority.MEDIUM, nullable=False
    )
    
    # Recipients
    recipient_address: Mapped[str] = mapped_column(String(255), nullable=False)
    
    # Content
    subject: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    data: Mapped[Optional[dict]] = mapped_column(JSON, nullable=True)
    
    # Delivery tracking
    sent_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    delivered_at: Mapped[Optional[datetime]] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    failed_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True), nullable=True)
    error_message: Mapped[Optional[str]] = mapped_column(Text, nullable=True)
    
    # Retry tracking
    retry_count: Mapped[int] = mapped_column(Integer, default=0, nullable=False)
    max_retries: Mapped[int] = mapped_column(Integer, default=3, nullable=False)
    next_retry_at: Mapped[Optional[datetime]] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
    
    # Provider response
    provider_message_id: Mapped[Optional[str]] = mapped_column(String(255), nullable=True)
    provider_response: Mapped[Optional[dict]] = mapped_column(JSON, nullable=True)
    
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=datetime.utcnow
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=datetime.utcnow, onupdate=datetime.utcnow
    )

    __table_args__ = (
        Index("idx_notif_user_id", "user_id"),
        Index("idx_notif_status", "status"),
        Index("idx_notif_created_at", "created_at"),
        Index("idx_notif_retry", "status", "next_retry_at"),
    )

"""Pydantic schemas for notifications."""
from datetime import datetime
from typing import Optional, Dict, Any, List
from uuid import UUID
from pydantic import BaseModel, Field, EmailStr, field_validator
from app.models import (
    NotificationChannel,
    NotificationStatus,
    NotificationPriority,
    NotificationType,
)


class FCMTokenCreate(BaseModel):
    """Request to register FCM token."""
    token: str = Field(..., min_length=20, max_length=255)
    device_type: str = Field(..., pattern="^(ios|android|web)$")
    device_id: Optional[str] = Field(None, max_length=255)


class FCMTokenResponse(BaseModel):
    """FCM token response."""
    id: UUID
    user_id: UUID
    token: str
    device_type: str
    device_id: Optional[str]
    is_active: bool
    created_at: datetime
    last_used_at: Optional[datetime]

    model_config = {"from_attributes": True}


class NotificationPreferencesUpdate(BaseModel):
    """Update notification preferences."""
    push_enabled: Optional[bool] = None
    sms_enabled: Optional[bool] = None
    email_enabled: Optional[bool] = None
    booking_notifications: Optional[bool] = None
    trip_notifications: Optional[bool] = None
    payment_notifications: Optional[bool] = None
    marketing_notifications: Optional[bool] = None


class NotificationPreferencesResponse(BaseModel):
    """Notification preferences response."""
    id: UUID
    user_id: UUID
    push_enabled: bool
    sms_enabled: bool
    email_enabled: bool
    booking_notifications: bool
    trip_notifications: bool
    payment_notifications: bool
    marketing_notifications: bool
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class SendNotificationRequest(BaseModel):
    """Request to send notification (internal API)."""
    user_id: UUID
    notification_type: NotificationType
    channels: List[NotificationChannel] = Field(
        default=[NotificationChannel.PUSH],
        min_length=1
    )
    priority: NotificationPriority = NotificationPriority.MEDIUM
    
    # Template data
    template_data: Dict[str, Any] = Field(default_factory=dict)
    
    # Override recipient (optional)
    push_tokens: Optional[List[str]] = None
    phone_number: Optional[str] = None
    email: Optional[str] = None
    
    # Custom content (bypasses templates)
    custom_subject: Optional[str] = None
    custom_body: Optional[str] = None


class BroadcastNotificationRequest(BaseModel):
    """Request to broadcast notification to multiple users."""
    user_ids: List[UUID] = Field(..., min_length=1, max_length=1000)
    notification_type: NotificationType
    channels: List[NotificationChannel] = Field(
        default=[NotificationChannel.PUSH],
        min_length=1
    )
    priority: NotificationPriority = NotificationPriority.MEDIUM
    template_data: Dict[str, Any] = Field(default_factory=dict)


class NotificationResponse(BaseModel):
    """Notification delivery response."""
    notification_id: UUID
    status: NotificationStatus
    message: str


class NotificationLogResponse(BaseModel):
    """Notification log details."""
    id: UUID
    user_id: UUID
    notification_type: NotificationType
    channel: NotificationChannel
    status: NotificationStatus
    priority: NotificationPriority
    recipient_address: str
    subject: Optional[str]
    body: str
    sent_at: Optional[datetime]
    delivered_at: Optional[datetime]
    failed_at: Optional[datetime]
    error_message: Optional[str]
    retry_count: int
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class NotificationHistoryResponse(BaseModel):
    """Paginated notification history."""
    notifications: List[NotificationLogResponse]
    total: int
    page: int
    page_size: int
    total_pages: int


class TemplateCreate(BaseModel):
    """Create notification template."""
    template_key: str = Field(..., min_length=1, max_length=100)
    notification_type: NotificationType
    channel: NotificationChannel
    subject_template: Optional[str] = Field(None, max_length=255)
    body_template: str = Field(..., min_length=1)


class TemplateUpdate(BaseModel):
    """Update notification template."""
    subject_template: Optional[str] = Field(None, max_length=255)
    body_template: Optional[str] = None
    is_active: Optional[bool] = None


class TemplateResponse(BaseModel):
    """Notification template response."""
    id: UUID
    template_key: str
    notification_type: NotificationType
    channel: NotificationChannel
    subject_template: Optional[str]
    body_template: str
    is_active: bool
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class NotificationStats(BaseModel):
    """Notification statistics."""
    total_sent: int
    total_delivered: int
    total_failed: int
    pending: int
    delivery_rate: float
    failure_rate: float
    average_delivery_time_seconds: Optional[float]


class HealthCheckResponse(BaseModel):
    """Health check response."""
    status: str
    service: str
    version: str
    timestamp: datetime
    dependencies: Dict[str, str]


# Aliases for backward compatibility with API endpoints
DeviceTokenCreate = FCMTokenCreate
DeviceTokenResponse = FCMTokenResponse
UserNotificationPreferenceUpdate = NotificationPreferencesUpdate
UserNotificationPreferenceResponse = NotificationPreferencesResponse
BroadcastRequest = BroadcastNotificationRequest
NotificationTemplateCreate = TemplateCreate
NotificationTemplateUpdate = TemplateUpdate
NotificationTemplateResponse = TemplateResponse

"""Pydantic schemas for user-related events."""

from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, Field


class UserRegisteredEvent(BaseModel):
    """User registration event schema."""

    event_type: str = Field(default="user.registered", const=True)
    event_timestamp: datetime
    user_id: UUID
    phone: str = Field(..., pattern=r"^\+234[7-9][0-1]\d{8}$")
    role: str = Field(..., pattern="^(RIDER|DRIVER|ADMIN)$")
    full_name: Optional[str] = None
    email: Optional[str] = None
    city: Optional[str] = None
    state: Optional[str] = None
    referral_code: Optional[str] = None
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "user.registered",
                "event_timestamp": "2024-11-14T10:30:00Z",
                "user_id": "123e4567-e89b-12d3-a456-426614174000",
                "phone": "+2348012345678",
                "role": "RIDER",
                "full_name": "John Doe",
                "email": "john@example.com",
                "city": "Lagos",
                "state": "Lagos",
            }
        }


class UserKYCVerifiedEvent(BaseModel):
    """User KYC verification event schema."""

    event_type: str = Field(default="user.kyc_verified", const=True)
    event_timestamp: datetime
    user_id: UUID
    kyc_status: str = Field(..., pattern="^(VERIFIED|REJECTED)$")
    verification_notes: Optional[str] = None
    verified_by: Optional[UUID] = None
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "user.kyc_verified",
                "event_timestamp": "2024-11-14T11:00:00Z",
                "user_id": "123e4567-e89b-12d3-a456-426614174000",
                "kyc_status": "VERIFIED",
                "verified_by": "admin-uuid",
            }
        }


class UserUpgradedToDriverEvent(BaseModel):
    """User upgraded to driver event schema."""

    event_type: str = Field(default="user.upgraded_to_driver", const=True)
    event_timestamp: datetime
    user_id: UUID
    previous_role: str = "RIDER"
    new_role: str = "DRIVER"
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "user.upgraded_to_driver",
                "event_timestamp": "2024-11-14T12:00:00Z",
                "user_id": "123e4567-e89b-12d3-a456-426614174000",
                "previous_role": "RIDER",
                "new_role": "DRIVER",
            }
        }


class UserActivityEvent(BaseModel):
    """User activity event schema (login, profile update, etc)."""

    event_type: str
    event_timestamp: datetime
    user_id: UUID
    activity_type: str  # 'login', 'profile_update', 'password_change'
    device_info: Optional[dict] = None
    ip_address: Optional[str] = None
    location: Optional[dict] = None

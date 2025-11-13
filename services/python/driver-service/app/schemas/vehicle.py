"""
Vehicle schemas for request/response validation.
"""
from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, Field, field_validator
import re


class VehicleBase(BaseModel):
    """Base vehicle schema with common fields."""
    
    plate_number: str = Field(..., min_length=1, max_length=20)
    make: str = Field(..., min_length=1, max_length=100)
    model: str = Field(..., min_length=1, max_length=100)
    year: int = Field(..., ge=1900, le=2030)
    color: str = Field(..., min_length=1, max_length=50)
    seats_total: int = Field(..., ge=1, le=50)
    vehicle_photo_url: Optional[str] = Field(None, max_length=500)
    
    @field_validator('plate_number')
    @classmethod
    def validate_plate_number(cls, v: str) -> str:
        """
        Validate Nigerian plate number format.
        Format: ABC-123DE or ABC123DE
        """
        v = v.upper().strip()
        # Allow flexible format for international plates
        if not re.match(r'^[A-Z0-9-]{5,15}$', v):
            raise ValueError('Invalid plate number format')
        return v


class VehicleCreate(VehicleBase):
    """Schema for creating a new vehicle."""
    pass


class VehicleUpdate(BaseModel):
    """Schema for updating an existing vehicle."""
    
    plate_number: Optional[str] = Field(None, min_length=1, max_length=20)
    make: Optional[str] = Field(None, min_length=1, max_length=100)
    model: Optional[str] = Field(None, min_length=1, max_length=100)
    year: Optional[int] = Field(None, ge=1900, le=2030)
    color: Optional[str] = Field(None, min_length=1, max_length=50)
    seats_total: Optional[int] = Field(None, ge=1, le=50)
    vehicle_photo_url: Optional[str] = Field(None, max_length=500)
    is_active: Optional[bool] = None


class VehicleResponse(VehicleBase):
    """Schema for vehicle response."""
    
    id: UUID
    driver_id: UUID
    is_verified: bool
    is_active: bool
    created_at: datetime
    updated_at: datetime
    
    class Config:
        from_attributes = True

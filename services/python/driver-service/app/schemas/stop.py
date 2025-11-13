"""
Stop schemas for request/response validation.
"""
from datetime import datetime
from typing import Optional
from uuid import UUID
from decimal import Decimal

from pydantic import BaseModel, Field, field_validator


class StopBase(BaseModel):
    """Base stop schema with common fields."""
    
    name: str = Field(..., min_length=1, max_length=255)
    lat: Decimal = Field(..., ge=-90, le=90, decimal_places=8)
    lon: Decimal = Field(..., ge=-180, le=180, decimal_places=8)
    address: Optional[str] = Field(None, max_length=500)
    landmark: Optional[str] = Field(None, max_length=255)


class StopCreate(StopBase):
    """Schema for creating a new stop."""
    pass


class StopResponse(StopBase):
    """Schema for stop response."""
    
    id: UUID
    created_at: datetime
    
    class Config:
        from_attributes = True


class StopSearch(BaseModel):
    """Schema for searching stops by proximity."""
    
    lat: Decimal = Field(..., ge=-90, le=90)
    lon: Decimal = Field(..., ge=-180, le=180)
    radius_km: float = Field(default=5.0, ge=0.1, le=50.0)
    limit: int = Field(default=20, ge=1, le=100)

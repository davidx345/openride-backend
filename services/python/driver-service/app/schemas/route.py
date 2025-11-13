"""
Route schemas for request/response validation.
"""
from datetime import datetime, time
from typing import Optional, List
from uuid import UUID
from decimal import Decimal

from pydantic import BaseModel, Field, field_validator, model_validator


class RouteStopInput(BaseModel):
    """Schema for route stop input."""
    
    stop_id: Optional[UUID] = None  # If stop exists
    name: Optional[str] = Field(None, min_length=1, max_length=255)
    lat: Optional[Decimal] = Field(None, ge=-90, le=90)
    lon: Optional[Decimal] = Field(None, ge=-180, le=180)
    address: Optional[str] = Field(None, max_length=500)
    landmark: Optional[str] = Field(None, max_length=255)
    planned_arrival_offset_minutes: int = Field(..., ge=0, le=1440)
    price_from_origin: Decimal = Field(..., ge=0, decimal_places=2)
    
    @model_validator(mode='after')
    def validate_stop_data(self):
        """Ensure either stop_id or coordinates are provided."""
        if self.stop_id is None:
            if self.name is None or self.lat is None or self.lon is None:
                raise ValueError(
                    'If stop_id is not provided, name, lat, and lon are required'
                )
        return self


class RouteStopResponse(BaseModel):
    """Schema for route stop response."""
    
    id: UUID
    stop_id: UUID
    stop_order: int
    stop_name: str
    stop_lat: Decimal
    stop_lon: Decimal
    planned_arrival_offset_minutes: int
    price_from_origin: Decimal
    
    class Config:
        from_attributes = True


class RouteBase(BaseModel):
    """Base route schema with common fields."""
    
    name: str = Field(..., min_length=1, max_length=255)
    departure_time: time
    active_days: List[int] = Field(..., min_length=1, max_length=7)
    seats_total: int = Field(..., ge=1, le=50)
    base_price: Decimal = Field(..., ge=0, decimal_places=2)
    schedule_rrule: Optional[str] = Field(None, max_length=500)
    notes: Optional[str] = Field(None, max_length=1000)
    
    @field_validator('active_days')
    @classmethod
    def validate_active_days(cls, v: List[int]) -> List[int]:
        """Validate active days are in range 0-6 (Mon-Sun)."""
        if not all(0 <= day <= 6 for day in v):
            raise ValueError('active_days must contain values between 0 and 6')
        if len(v) != len(set(v)):
            raise ValueError('active_days must not contain duplicates')
        return sorted(v)


class RouteCreate(RouteBase):
    """Schema for creating a new route."""
    
    vehicle_id: UUID
    stops: List[RouteStopInput] = Field(..., min_length=2)
    
    @field_validator('stops')
    @classmethod
    def validate_stops_order(cls, v: List[RouteStopInput]) -> List[RouteStopInput]:
        """Validate stops are properly ordered."""
        if len(v) < 2:
            raise ValueError('Route must have at least 2 stops (origin and destination)')
        
        # Check that arrival offsets are strictly increasing
        offsets = [stop.planned_arrival_offset_minutes for stop in v]
        if offsets[0] != 0:
            raise ValueError('First stop must have arrival offset of 0')
        
        for i in range(1, len(offsets)):
            if offsets[i] <= offsets[i-1]:
                raise ValueError('Arrival offsets must be strictly increasing')
        
        # Check that prices are non-decreasing
        prices = [float(stop.price_from_origin) for stop in v]
        if prices[0] != 0:
            raise ValueError('First stop must have price of 0')
        
        for i in range(1, len(prices)):
            if prices[i] < prices[i-1]:
                raise ValueError('Prices must be non-decreasing')
        
        return v


class RouteUpdate(BaseModel):
    """Schema for updating an existing route."""
    
    name: Optional[str] = Field(None, min_length=1, max_length=255)
    departure_time: Optional[time] = None
    active_days: Optional[List[int]] = Field(None, min_length=1, max_length=7)
    base_price: Optional[Decimal] = Field(None, ge=0)
    schedule_rrule: Optional[str] = Field(None, max_length=500)
    notes: Optional[str] = Field(None, max_length=1000)
    status: Optional[str] = None  # ACTIVE, PAUSED, CANCELLED


class RouteResponse(RouteBase):
    """Schema for route response."""
    
    id: UUID
    driver_id: UUID
    vehicle_id: UUID
    seats_available: int
    status: str
    stops: List[RouteStopResponse] = []
    created_at: datetime
    updated_at: datetime
    
    class Config:
        from_attributes = True

"""Pydantic schemas for route-related events."""

from datetime import datetime, time
from decimal import Decimal
from typing import List, Optional
from uuid import UUID

from pydantic import BaseModel, Field


class RouteCreatedEvent(BaseModel):
    """Route created event schema."""

    event_type: str = Field(default="route.created", const=True)
    event_timestamp: datetime
    route_id: UUID
    driver_id: UUID
    route_name: str
    origin_city: str
    destination_city: str
    origin_state: str
    destination_state: str
    stops_count: int
    seats_total: int = Field(..., ge=1, le=8)
    base_price: Decimal
    departure_time: time
    active_days: List[str]  # ['MON', 'TUE', 'WED', 'THU', 'FRI']
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "route.created",
                "event_timestamp": "2024-11-14T09:00:00Z",
                "route_id": "rt123456-e89b-12d3-a456-426614174000",
                "driver_id": "d1234567-e89b-12d3-a456-426614174000",
                "route_name": "Lekki to VI Express",
                "origin_city": "Lagos",
                "destination_city": "Lagos",
                "origin_state": "Lagos",
                "destination_state": "Lagos",
                "stops_count": 5,
                "seats_total": 4,
                "base_price": "1500.00",
                "departure_time": "07:00:00",
                "active_days": ["MON", "TUE", "WED", "THU", "FRI"],
            }
        }


class RouteActivatedEvent(BaseModel):
    """Route activated event schema."""

    event_type: str = Field(default="route.activated", const=True)
    event_timestamp: datetime
    route_id: UUID
    driver_id: UUID
    route_name: str
    previous_status: str = "PAUSED"
    new_status: str = "ACTIVE"
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "route.activated",
                "event_timestamp": "2024-11-14T09:30:00Z",
                "route_id": "rt123456-e89b-12d3-a456-426614174000",
                "driver_id": "d1234567-e89b-12d3-a456-426614174000",
                "route_name": "Lekki to VI Express",
                "previous_status": "PAUSED",
                "new_status": "ACTIVE",
            }
        }


class RouteCancelledEvent(BaseModel):
    """Route cancelled event schema."""

    event_type: str = Field(default="route.cancelled", const=True)
    event_timestamp: datetime
    route_id: UUID
    driver_id: UUID
    route_name: str
    cancellation_reason: Optional[str] = None
    active_bookings_count: int = 0  # Number of affected bookings
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "route.cancelled",
                "event_timestamp": "2024-11-14T10:00:00Z",
                "route_id": "rt123456-e89b-12d3-a456-426614174000",
                "driver_id": "d1234567-e89b-12d3-a456-426614174000",
                "route_name": "Lekki to VI Express",
                "cancellation_reason": "Vehicle sold",
                "active_bookings_count": 3,
            }
        }


class RouteUpdatedEvent(BaseModel):
    """Route updated event schema."""

    event_type: str = Field(default="route.updated", const=True)
    event_timestamp: datetime
    route_id: UUID
    driver_id: UUID
    route_name: str
    updated_fields: List[str]  # List of fields that were updated
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "route.updated",
                "event_timestamp": "2024-11-14T11:00:00Z",
                "route_id": "rt123456-e89b-12d3-a456-426614174000",
                "driver_id": "d1234567-e89b-12d3-a456-426614174000",
                "route_name": "Lekki to VI Express",
                "updated_fields": ["base_price", "departure_time"],
            }
        }

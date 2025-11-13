"""Pydantic schemas for trip-related events."""

from datetime import datetime
from decimal import Decimal
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, Field


class TripStartedEvent(BaseModel):
    """Trip started event schema."""

    event_type: str = Field(default="trip.started", const=True)
    event_timestamp: datetime
    trip_id: UUID
    booking_id: UUID
    driver_id: UUID
    rider_id: UUID
    route_id: UUID
    scheduled_departure: datetime
    actual_departure: datetime
    start_lat: float
    start_lon: float
    passengers_count: int = Field(..., ge=1, le=8)
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "trip.started",
                "event_timestamp": "2024-11-15T07:05:00Z",
                "trip_id": "t1234567-e89b-12d3-a456-426614174000",
                "booking_id": "b1234567-e89b-12d3-a456-426614174000",
                "driver_id": "d1234567-e89b-12d3-a456-426614174000",
                "rider_id": "r1234567-e89b-12d3-a456-426614174000",
                "route_id": "rt123456-e89b-12d3-a456-426614174000",
                "scheduled_departure": "2024-11-15T07:00:00Z",
                "actual_departure": "2024-11-15T07:05:00Z",
                "start_lat": 6.5244,
                "start_lon": 3.3792,
                "passengers_count": 2,
            }
        }


class TripCompletedEvent(BaseModel):
    """Trip completed event schema."""

    event_type: str = Field(default="trip.completed", const=True)
    event_timestamp: datetime
    trip_id: UUID
    booking_id: UUID
    driver_id: UUID
    rider_id: UUID
    route_id: UUID
    actual_departure: datetime
    actual_arrival: datetime
    duration_minutes: int
    distance_km: float
    end_lat: float
    end_lon: float
    driver_earnings: Decimal
    platform_commission: Decimal
    on_time: bool  # True if departed within 10 minutes of scheduled time
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "trip.completed",
                "event_timestamp": "2024-11-15T08:25:00Z",
                "trip_id": "t1234567-e89b-12d3-a456-426614174000",
                "booking_id": "b1234567-e89b-12d3-a456-426614174000",
                "driver_id": "d1234567-e89b-12d3-a456-426614174000",
                "rider_id": "r1234567-e89b-12d3-a456-426614174000",
                "route_id": "rt123456-e89b-12d3-a456-426614174000",
                "actual_departure": "2024-11-15T07:05:00Z",
                "actual_arrival": "2024-11-15T08:25:00Z",
                "duration_minutes": 80,
                "distance_km": 25.5,
                "end_lat": 6.4281,
                "end_lon": 3.4219,
                "driver_earnings": "2550.00",
                "platform_commission": "450.00",
                "on_time": true,
            }
        }


class TripCancelledEvent(BaseModel):
    """Trip cancelled event schema."""

    event_type: str = Field(default="trip.cancelled", const=True)
    event_timestamp: datetime
    trip_id: UUID
    booking_id: UUID
    driver_id: UUID
    rider_id: UUID
    route_id: UUID
    cancellation_reason: str
    cancelled_by: str = Field(..., pattern="^(DRIVER|RIDER|SYSTEM)$")
    was_started: bool = False
    partial_distance_km: Optional[float] = None
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "trip.cancelled",
                "event_timestamp": "2024-11-15T07:30:00Z",
                "trip_id": "t1234567-e89b-12d3-a456-426614174000",
                "booking_id": "b1234567-e89b-12d3-a456-426614174000",
                "driver_id": "d1234567-e89b-12d3-a456-426614174000",
                "rider_id": "r1234567-e89b-12d3-a456-426614174000",
                "route_id": "rt123456-e89b-12d3-a456-426614174000",
                "cancellation_reason": "Vehicle breakdown",
                "cancelled_by": "DRIVER",
                "was_started": true,
                "partial_distance_km": 5.2,
            }
        }

"""Pydantic schemas for booking-related events."""

from datetime import datetime
from decimal import Decimal
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, Field


class BookingCreatedEvent(BaseModel):
    """Booking created event schema."""

    event_type: str = Field(default="booking.created", const=True)
    event_timestamp: datetime
    booking_id: UUID
    rider_id: UUID
    driver_id: UUID
    route_id: UUID
    seats_booked: int = Field(..., ge=1, le=8)
    total_price: Decimal = Field(..., decimal_places=2)
    booking_status: str = "PENDING"
    pickup_stop_id: UUID
    dropoff_stop_id: UUID
    pickup_lat: float
    pickup_lon: float
    dropoff_lat: float
    dropoff_lon: float
    scheduled_departure: datetime
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "booking.created",
                "event_timestamp": "2024-11-14T10:00:00Z",
                "booking_id": "b1234567-e89b-12d3-a456-426614174000",
                "rider_id": "r1234567-e89b-12d3-a456-426614174000",
                "driver_id": "d1234567-e89b-12d3-a456-426614174000",
                "route_id": "rt123456-e89b-12d3-a456-426614174000",
                "seats_booked": 2,
                "total_price": "3000.00",
                "booking_status": "PENDING",
                "pickup_stop_id": "s1234567-e89b-12d3-a456-426614174000",
                "dropoff_stop_id": "s2234567-e89b-12d3-a456-426614174000",
                "pickup_lat": 6.5244,
                "pickup_lon": 3.3792,
                "dropoff_lat": 6.4281,
                "dropoff_lon": 3.4219,
                "scheduled_departure": "2024-11-15T07:00:00Z",
            }
        }


class BookingConfirmedEvent(BaseModel):
    """Booking confirmed event schema (after payment success)."""

    event_type: str = Field(default="booking.confirmed", const=True)
    event_timestamp: datetime
    booking_id: UUID
    rider_id: UUID
    driver_id: UUID
    route_id: UUID
    seats_booked: int
    total_price: Decimal
    payment_id: UUID
    confirmation_code: str
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "booking.confirmed",
                "event_timestamp": "2024-11-14T10:05:00Z",
                "booking_id": "b1234567-e89b-12d3-a456-426614174000",
                "rider_id": "r1234567-e89b-12d3-a456-426614174000",
                "driver_id": "d1234567-e89b-12d3-a456-426614174000",
                "route_id": "rt123456-e89b-12d3-a456-426614174000",
                "seats_booked": 2,
                "total_price": "3000.00",
                "payment_id": "p1234567-e89b-12d3-a456-426614174000",
                "confirmation_code": "ABC123",
            }
        }


class BookingCancelledEvent(BaseModel):
    """Booking cancelled event schema."""

    event_type: str = Field(default="booking.cancelled", const=True)
    event_timestamp: datetime
    booking_id: UUID
    rider_id: UUID
    driver_id: UUID
    route_id: UUID
    cancellation_reason: Optional[str] = None
    cancelled_by: str = Field(..., pattern="^(RIDER|DRIVER|SYSTEM)$")
    refund_initiated: bool = False
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "booking.cancelled",
                "event_timestamp": "2024-11-14T10:30:00Z",
                "booking_id": "b1234567-e89b-12d3-a456-426614174000",
                "rider_id": "r1234567-e89b-12d3-a456-426614174000",
                "driver_id": "d1234567-e89b-12d3-a456-426614174000",
                "route_id": "rt123456-e89b-12d3-a456-426614174000",
                "cancellation_reason": "Change of plans",
                "cancelled_by": "RIDER",
                "refund_initiated": true,
            }
        }


class BookingCompletedEvent(BaseModel):
    """Booking completed event schema (trip finished)."""

    event_type: str = Field(default="booking.completed", const=True)
    event_timestamp: datetime
    booking_id: UUID
    rider_id: UUID
    driver_id: UUID
    route_id: UUID
    trip_id: UUID
    seats_booked: int
    total_price: Decimal
    actual_departure: datetime
    actual_arrival: datetime
    duration_minutes: int
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "booking.completed",
                "event_timestamp": "2024-11-15T08:30:00Z",
                "booking_id": "b1234567-e89b-12d3-a456-426614174000",
                "rider_id": "r1234567-e89b-12d3-a456-426614174000",
                "driver_id": "d1234567-e89b-12d3-a456-426614174000",
                "route_id": "rt123456-e89b-12d3-a456-426614174000",
                "trip_id": "t1234567-e89b-12d3-a456-426614174000",
                "seats_booked": 2,
                "total_price": "3000.00",
                "actual_departure": "2024-11-15T07:05:00Z",
                "actual_arrival": "2024-11-15T08:25:00Z",
                "duration_minutes": 80,
            }
        }

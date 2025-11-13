"""Pydantic schemas for trip tracking"""
from datetime import datetime
from decimal import Decimal
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, Field

from app.schemas.location import LocationPoint


class TripCreate(BaseModel):
    """Create new trip tracking record"""
    
    trip_id: UUID
    booking_id: UUID
    driver_id: UUID
    rider_id: UUID
    pickup_location: LocationPoint
    dropoff_location: LocationPoint
    scheduled_time: datetime
    
    class Config:
        json_schema_extra = {
            "example": {
                "trip_id": "123e4567-e89b-12d3-a456-426614174000",
                "booking_id": "123e4567-e89b-12d3-a456-426614174001",
                "driver_id": "123e4567-e89b-12d3-a456-426614174002",
                "rider_id": "123e4567-e89b-12d3-a456-426614174003",
                "pickup_location": {
                    "latitude": 37.7749,
                    "longitude": -122.4194
                },
                "dropoff_location": {
                    "latitude": 37.8049,
                    "longitude": -122.4394
                },
                "scheduled_time": "2024-12-15T11:00:00Z"
            }
        }


class TripStatusUpdate(BaseModel):
    """Update trip status"""
    
    status: str = Field(
        ...,
        pattern="^(PENDING|EN_ROUTE|ARRIVED|IN_PROGRESS|COMPLETED|CANCELLED)$"
    )
    estimated_arrival: Optional[datetime] = None
    
    class Config:
        json_schema_extra = {
            "example": {
                "status": "EN_ROUTE",
                "estimated_arrival": "2024-12-15T11:15:00Z"
            }
        }


class TripResponse(BaseModel):
    """Trip tracking data for API responses"""
    
    id: UUID
    trip_id: UUID
    booking_id: UUID
    driver_id: UUID
    rider_id: UUID
    status: str
    pickup_location: LocationPoint
    dropoff_location: LocationPoint
    scheduled_time: datetime
    started_at: Optional[datetime]
    arrived_at: Optional[datetime]
    pickup_time: Optional[datetime]
    completed_at: Optional[datetime]
    cancelled_at: Optional[datetime]
    estimated_arrival: Optional[datetime]
    distance_meters: Optional[int]
    duration_seconds: Optional[int]
    created_at: datetime
    updated_at: datetime
    
    class Config:
        from_attributes = True
        json_schema_extra = {
            "example": {
                "id": "123e4567-e89b-12d3-a456-426614174000",
                "trip_id": "123e4567-e89b-12d3-a456-426614174001",
                "booking_id": "123e4567-e89b-12d3-a456-426614174002",
                "driver_id": "123e4567-e89b-12d3-a456-426614174003",
                "rider_id": "123e4567-e89b-12d3-a456-426614174004",
                "status": "EN_ROUTE",
                "pickup_location": {
                    "latitude": 37.7749,
                    "longitude": -122.4194
                },
                "dropoff_location": {
                    "latitude": 37.8049,
                    "longitude": -122.4394
                },
                "scheduled_time": "2024-12-15T11:00:00Z",
                "started_at": "2024-12-15T10:50:00Z",
                "estimated_arrival": "2024-12-15T11:15:00Z",
                "created_at": "2024-12-15T10:30:00Z",
                "updated_at": "2024-12-15T10:50:00Z"
            }
        }


class TripListQuery(BaseModel):
    """Query parameters for trip list"""
    
    driver_id: Optional[UUID] = None
    rider_id: Optional[UUID] = None
    status: Optional[str] = Field(
        None,
        pattern="^(PENDING|EN_ROUTE|ARRIVED|IN_PROGRESS|COMPLETED|CANCELLED)$"
    )
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    limit: int = Field(default=50, ge=1, le=100)
    offset: int = Field(default=0, ge=0)
    
    class Config:
        json_schema_extra = {
            "example": {
                "driver_id": "123e4567-e89b-12d3-a456-426614174003",
                "status": "EN_ROUTE",
                "limit": 50,
                "offset": 0
            }
        }


class TripMetrics(BaseModel):
    """Trip metrics and statistics"""
    
    trip_id: UUID
    total_distance_meters: int
    total_duration_seconds: int
    average_speed_kmh: Decimal
    max_speed_kmh: Decimal
    idle_time_seconds: int
    
    class Config:
        json_schema_extra = {
            "example": {
                "trip_id": "123e4567-e89b-12d3-a456-426614174001",
                "total_distance_meters": 5000,
                "total_duration_seconds": 900,
                "average_speed_kmh": 30.5,
                "max_speed_kmh": 45.0,
                "idle_time_seconds": 120
            }
        }

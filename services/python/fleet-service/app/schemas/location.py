"""Pydantic schemas for location tracking"""
from datetime import datetime
from decimal import Decimal
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, Field, field_validator


class LocationPoint(BaseModel):
    """Geographic location point"""
    
    latitude: Decimal = Field(..., ge=-90, le=90, description="Latitude in decimal degrees")
    longitude: Decimal = Field(..., ge=-180, le=180, description="Longitude in decimal degrees")
    
    class Config:
        json_schema_extra = {
            "example": {
                "latitude": 37.7749,
                "longitude": -122.4194
            }
        }


class DriverLocationUpdate(BaseModel):
    """Driver location update from mobile app"""
    
    latitude: Decimal = Field(..., ge=-90, le=90)
    longitude: Decimal = Field(..., ge=-180, le=180)
    bearing: Optional[Decimal] = Field(None, ge=0, le=360, description="Direction in degrees")
    speed: Optional[Decimal] = Field(None, ge=0, description="Speed in km/h")
    accuracy: Optional[Decimal] = Field(None, ge=0, description="GPS accuracy in meters")
    altitude: Optional[Decimal] = Field(None, description="Altitude in meters")
    timestamp: Optional[datetime] = Field(None, description="Client timestamp")
    
    @field_validator('bearing')
    @classmethod
    def validate_bearing(cls, v: Optional[Decimal]) -> Optional[Decimal]:
        if v is not None and (v < 0 or v > 360):
            raise ValueError('Bearing must be between 0 and 360 degrees')
        return v
    
    class Config:
        json_schema_extra = {
            "example": {
                "latitude": 37.7749,
                "longitude": -122.4194,
                "bearing": 45.5,
                "speed": 30.0,
                "accuracy": 5.0,
                "altitude": 10.0,
                "timestamp": "2024-12-15T10:30:00Z"
            }
        }


class DriverLocationResponse(BaseModel):
    """Driver location data for API responses"""
    
    id: UUID
    driver_id: UUID
    latitude: Decimal
    longitude: Decimal
    bearing: Optional[Decimal]
    speed: Optional[Decimal]
    accuracy: Optional[Decimal]
    altitude: Optional[Decimal]
    status: str
    created_at: datetime
    updated_at: datetime
    
    class Config:
        from_attributes = True
        json_schema_extra = {
            "example": {
                "id": "123e4567-e89b-12d3-a456-426614174000",
                "driver_id": "123e4567-e89b-12d3-a456-426614174001",
                "latitude": 37.7749,
                "longitude": -122.4194,
                "bearing": 45.5,
                "speed": 30.0,
                "accuracy": 5.0,
                "altitude": 10.0,
                "status": "ONLINE",
                "created_at": "2024-12-15T10:30:00Z",
                "updated_at": "2024-12-15T10:30:00Z"
            }
        }


class DriverStatusUpdate(BaseModel):
    """Driver online/offline status update"""
    
    status: str = Field(..., pattern="^(OFFLINE|ONLINE|BUSY|ON_TRIP)$")
    location: Optional[DriverLocationUpdate] = None
    
    class Config:
        json_schema_extra = {
            "example": {
                "status": "ONLINE",
                "location": {
                    "latitude": 37.7749,
                    "longitude": -122.4194,
                    "accuracy": 5.0
                }
            }
        }


class LocationHistoryQuery(BaseModel):
    """Query parameters for location history"""
    
    driver_id: UUID
    trip_id: Optional[UUID] = None
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    limit: int = Field(default=100, ge=1, le=1000)
    
    class Config:
        json_schema_extra = {
            "example": {
                "driver_id": "123e4567-e89b-12d3-a456-426614174001",
                "trip_id": "123e4567-e89b-12d3-a456-426614174002",
                "start_time": "2024-12-15T10:00:00Z",
                "end_time": "2024-12-15T11:00:00Z",
                "limit": 100
            }
        }


class LocationHistoryResponse(BaseModel):
    """Historical location point"""
    
    id: UUID
    driver_id: UUID
    trip_id: Optional[UUID]
    latitude: Decimal
    longitude: Decimal
    bearing: Optional[Decimal]
    speed: Optional[Decimal]
    accuracy: Optional[Decimal]
    recorded_at: datetime
    
    class Config:
        from_attributes = True


class NearbyDriversQuery(BaseModel):
    """Query for nearby available drivers"""
    
    latitude: Decimal = Field(..., ge=-90, le=90)
    longitude: Decimal = Field(..., ge=-180, le=180)
    radius_meters: int = Field(default=5000, ge=100, le=50000, description="Search radius")
    limit: int = Field(default=10, ge=1, le=50)
    
    class Config:
        json_schema_extra = {
            "example": {
                "latitude": 37.7749,
                "longitude": -122.4194,
                "radius_meters": 5000,
                "limit": 10
            }
        }


class NearbyDriverResponse(BaseModel):
    """Nearby driver information"""
    
    driver_id: UUID
    latitude: Decimal
    longitude: Decimal
    bearing: Optional[Decimal]
    distance_meters: int
    status: str
    updated_at: datetime
    
    class Config:
        json_schema_extra = {
            "example": {
                "driver_id": "123e4567-e89b-12d3-a456-426614174001",
                "latitude": 37.7749,
                "longitude": -122.4194,
                "bearing": 45.5,
                "distance_meters": 1500,
                "status": "ONLINE",
                "updated_at": "2024-12-15T10:30:00Z"
            }
        }

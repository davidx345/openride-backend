"""Pydantic schemas for WebSocket events"""
from datetime import datetime
from decimal import Decimal
from typing import Any, Dict, Optional
from uuid import UUID

from pydantic import BaseModel, Field

from app.schemas.location import LocationPoint


class WebSocketMessage(BaseModel):
    """Base WebSocket message structure"""
    
    event: str
    data: Dict[str, Any]
    timestamp: datetime = Field(default_factory=datetime.utcnow)
    
    class Config:
        json_schema_extra = {
            "example": {
                "event": "driver:location",
                "data": {
                    "latitude": 37.7749,
                    "longitude": -122.4194
                },
                "timestamp": "2024-12-15T10:30:00Z"
            }
        }


class DriverLocationEvent(BaseModel):
    """Driver location update event"""
    
    driver_id: UUID
    latitude: Decimal
    longitude: Decimal
    bearing: Optional[Decimal] = None
    speed: Optional[Decimal] = None
    accuracy: Optional[Decimal] = None
    status: str
    timestamp: datetime
    
    class Config:
        json_schema_extra = {
            "example": {
                "driver_id": "123e4567-e89b-12d3-a456-426614174001",
                "latitude": 37.7749,
                "longitude": -122.4194,
                "bearing": 45.5,
                "speed": 30.0,
                "accuracy": 5.0,
                "status": "ON_TRIP",
                "timestamp": "2024-12-15T10:30:00Z"
            }
        }


class DriverStatusEvent(BaseModel):
    """Driver online/offline status event"""
    
    driver_id: UUID
    status: str = Field(..., pattern="^(OFFLINE|ONLINE|BUSY|ON_TRIP)$")
    location: Optional[LocationPoint] = None
    timestamp: datetime
    
    class Config:
        json_schema_extra = {
            "example": {
                "driver_id": "123e4567-e89b-12d3-a456-426614174001",
                "status": "ONLINE",
                "location": {
                    "latitude": 37.7749,
                    "longitude": -122.4194
                },
                "timestamp": "2024-12-15T10:30:00Z"
            }
        }


class TripUpdateEvent(BaseModel):
    """Trip status update event"""
    
    trip_id: UUID
    status: str = Field(
        ...,
        pattern="^(PENDING|EN_ROUTE|ARRIVED|IN_PROGRESS|COMPLETED|CANCELLED)$"
    )
    driver_location: Optional[LocationPoint] = None
    estimated_arrival: Optional[datetime] = None
    distance_remaining: Optional[int] = None
    timestamp: datetime
    
    class Config:
        json_schema_extra = {
            "example": {
                "trip_id": "123e4567-e89b-12d3-a456-426614174001",
                "status": "EN_ROUTE",
                "driver_location": {
                    "latitude": 37.7749,
                    "longitude": -122.4194
                },
                "estimated_arrival": "2024-12-15T11:15:00Z",
                "distance_remaining": 3000,
                "timestamp": "2024-12-15T10:30:00Z"
            }
        }


class RiderSubscribeEvent(BaseModel):
    """Rider subscription to trip updates"""
    
    trip_id: UUID
    rider_id: UUID
    
    class Config:
        json_schema_extra = {
            "example": {
                "trip_id": "123e4567-e89b-12d3-a456-426614174001",
                "rider_id": "123e4567-e89b-12d3-a456-426614174002"
            }
        }


class RiderUnsubscribeEvent(BaseModel):
    """Rider unsubscription from trip updates"""
    
    trip_id: UUID
    rider_id: UUID
    
    class Config:
        json_schema_extra = {
            "example": {
                "trip_id": "123e4567-e89b-12d3-a456-426614174001",
                "rider_id": "123e4567-e89b-12d3-a456-426614174002"
            }
        }


class ErrorEvent(BaseModel):
    """Error event for WebSocket"""
    
    error_code: str
    message: str
    details: Optional[Dict[str, Any]] = None
    
    class Config:
        json_schema_extra = {
            "example": {
                "error_code": "RATE_LIMIT_EXCEEDED",
                "message": "Location update rate limit exceeded",
                "details": {
                    "retry_after": 5
                }
            }
        }


class ConnectionEvent(BaseModel):
    """Connection/disconnection event"""
    
    user_id: UUID
    user_role: str = Field(..., pattern="^(DRIVER|RIDER)$")
    session_id: str
    event_type: str = Field(..., pattern="^(CONNECTED|DISCONNECTED)$")
    timestamp: datetime
    
    class Config:
        json_schema_extra = {
            "example": {
                "user_id": "123e4567-e89b-12d3-a456-426614174001",
                "user_role": "DRIVER",
                "session_id": "socket_123456",
                "event_type": "CONNECTED",
                "timestamp": "2024-12-15T10:30:00Z"
            }
        }

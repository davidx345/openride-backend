"""Pydantic schemas for location-related events."""

from datetime import datetime
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, Field


class DriverLocationUpdatedEvent(BaseModel):
    """Driver location updated event schema (sampled)."""

    event_type: str = Field(default="driver.location.updated", const=True)
    event_timestamp: datetime
    driver_id: UUID
    trip_id: Optional[UUID] = None
    lat: float = Field(..., ge=-90, le=90)
    lon: float = Field(..., ge=-180, le=180)
    speed_kmh: Optional[float] = Field(None, ge=0)
    bearing: Optional[float] = Field(None, ge=0, lt=360)
    accuracy_meters: Optional[float] = Field(None, ge=0)
    city: Optional[str] = None
    state: Optional[str] = None
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "driver.location.updated",
                "event_timestamp": "2024-11-15T07:15:00Z",
                "driver_id": "d1234567-e89b-12d3-a456-426614174000",
                "trip_id": "t1234567-e89b-12d3-a456-426614174000",
                "lat": 6.5244,
                "lon": 3.3792,
                "speed_kmh": 45.5,
                "bearing": 120.5,
                "accuracy_meters": 10.0,
                "city": "Lagos",
                "state": "Lagos",
            }
        }

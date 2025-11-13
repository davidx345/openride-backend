"""Driver location REST API endpoints"""
from decimal import Decimal
from typing import List, Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.redis import get_redis
from app.db import get_db
from app.schemas.location import (
    DriverLocationResponse,
    DriverLocationUpdate,
    DriverStatusUpdate,
    LocationHistoryQuery,
    LocationHistoryResponse,
    NearbyDriverResponse,
    NearbyDriversQuery,
)
from app.services.location import LocationService, RateLimitError

router = APIRouter(prefix="/v1/drivers", tags=["drivers"])


@router.post("/{driver_id}/location", response_model=DriverLocationResponse)
async def update_driver_location(
    driver_id: UUID,
    location_data: DriverLocationUpdate,
    db: AsyncSession = Depends(get_db),
    redis = Depends(get_redis),
):
    """Update driver's current location"""
    location_service = LocationService(db, redis, None)
    
    try:
        location = await location_service.update_driver_location(
            driver_id=driver_id,
            latitude=location_data.latitude,
            longitude=location_data.longitude,
            bearing=location_data.bearing,
            speed=location_data.speed,
            accuracy=location_data.accuracy,
            altitude=location_data.altitude,
        )
    except RateLimitError as e:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=str(e)
        )
    
    return DriverLocationResponse.from_orm(location)


@router.get("/{driver_id}/location", response_model=DriverLocationResponse)
async def get_driver_location(
    driver_id: UUID,
    db: AsyncSession = Depends(get_db),
    redis = Depends(get_redis),
):
    """Get driver's current location"""
    location_service = LocationService(db, redis, None)
    location = await location_service.get_driver_location(driver_id)
    
    if not location:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Location not found for driver: {driver_id}"
        )
    
    return DriverLocationResponse.from_orm(location)


@router.post("/{driver_id}/status", status_code=status.HTTP_204_NO_CONTENT)
async def update_driver_status(
    driver_id: UUID,
    status_data: DriverStatusUpdate,
    db: AsyncSession = Depends(get_db),
    redis = Depends(get_redis),
):
    """Update driver online/offline status"""
    location_service = LocationService(db, redis, None)
    
    location_dict = None
    if status_data.location:
        location_dict = {
            'latitude': status_data.location.latitude,
            'longitude': status_data.location.longitude,
            'bearing': status_data.location.bearing,
            'speed': status_data.location.speed,
            'accuracy': status_data.location.accuracy,
            'altitude': status_data.location.altitude,
        }
    
    await location_service.update_driver_status(
        driver_id=driver_id,
        status=status_data.status,
        location_data=location_dict,
    )


@router.post("/nearby", response_model=List[NearbyDriverResponse])
async def find_nearby_drivers(
    query: NearbyDriversQuery,
    db: AsyncSession = Depends(get_db),
    redis = Depends(get_redis),
):
    """Find nearby available drivers using PostGIS"""
    location_service = LocationService(db, redis, None)
    
    nearby_drivers = await location_service.find_nearby_drivers(
        latitude=query.latitude,
        longitude=query.longitude,
        radius_meters=query.radius_meters,
        limit=query.limit,
    )
    
    return nearby_drivers


@router.get("/{driver_id}/history", response_model=List[LocationHistoryResponse])
async def get_location_history(
    driver_id: UUID,
    trip_id: Optional[UUID] = None,
    limit: int = 100,
    db: AsyncSession = Depends(get_db),
    redis = Depends(get_redis),
):
    """Get driver's location history"""
    location_service = LocationService(db, redis, None)
    
    history = await location_service.get_location_history(
        driver_id=driver_id,
        trip_id=trip_id,
        limit=limit,
    )
    
    return [LocationHistoryResponse.from_orm(h) for h in history]

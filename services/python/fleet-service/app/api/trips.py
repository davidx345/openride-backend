"""Trip tracking REST API endpoints"""
from datetime import datetime
from typing import List, Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_db
from app.core.redis import get_redis
from app.schemas.trip import (
    TripCreate,
    TripListQuery,
    TripResponse,
    TripStatusUpdate,
)
from app.services.trip import TripService

router = APIRouter(prefix="/v1/trips", tags=["trips"])


@router.post("", response_model=TripResponse, status_code=status.HTTP_201_CREATED)
async def create_trip(
    trip_data: TripCreate,
    db: AsyncSession = Depends(get_db),
    redis = Depends(get_redis),
):
    """Create a new trip tracking record"""
    trip_service = TripService(db, redis, None)  # sio not needed for REST
    trip = await trip_service.create_trip(trip_data)
    
    return TripResponse(
        id=trip.id,
        trip_id=trip.trip_id,
        booking_id=trip.booking_id,
        driver_id=trip.driver_id,
        rider_id=trip.rider_id,
        status=trip.status,
        pickup_location={
            'latitude': trip.pickup_latitude,
            'longitude': trip.pickup_longitude,
        },
        dropoff_location={
            'latitude': trip.dropoff_latitude,
            'longitude': trip.dropoff_longitude,
        },
        scheduled_time=trip.scheduled_time,
        started_at=trip.started_at,
        arrived_at=trip.arrived_at,
        pickup_time=trip.pickup_time,
        completed_at=trip.completed_at,
        cancelled_at=trip.cancelled_at,
        estimated_arrival=trip.estimated_arrival,
        distance_meters=trip.distance_meters,
        duration_seconds=trip.duration_seconds,
        created_at=trip.created_at,
        updated_at=trip.updated_at,
    )


@router.get("/{trip_id}", response_model=TripResponse)
async def get_trip(
    trip_id: UUID,
    db: AsyncSession = Depends(get_db),
    redis = Depends(get_redis),
):
    """Get trip details by ID"""
    trip_service = TripService(db, redis, None)
    trip = await trip_service.get_trip(trip_id)
    
    if not trip:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Trip not found: {trip_id}"
        )
    
    return TripResponse(
        id=trip.id,
        trip_id=trip.trip_id,
        booking_id=trip.booking_id,
        driver_id=trip.driver_id,
        rider_id=trip.rider_id,
        status=trip.status,
        pickup_location={
            'latitude': trip.pickup_latitude,
            'longitude': trip.pickup_longitude,
        },
        dropoff_location={
            'latitude': trip.dropoff_latitude,
            'longitude': trip.dropoff_longitude,
        },
        scheduled_time=trip.scheduled_time,
        started_at=trip.started_at,
        arrived_at=trip.arrived_at,
        pickup_time=trip.pickup_time,
        completed_at=trip.completed_at,
        cancelled_at=trip.cancelled_at,
        estimated_arrival=trip.estimated_arrival,
        distance_meters=trip.distance_meters,
        duration_seconds=trip.duration_seconds,
        created_at=trip.created_at,
        updated_at=trip.updated_at,
    )


@router.patch("/{trip_id}/status", response_model=TripResponse)
async def update_trip_status(
    trip_id: UUID,
    status_update: TripStatusUpdate,
    db: AsyncSession = Depends(get_db),
    redis = Depends(get_redis),
):
    """Update trip status"""
    trip_service = TripService(db, redis, None)
    
    try:
        trip = await trip_service.update_trip_status(
            trip_id=trip_id,
            status=status_update.status,
            estimated_arrival=status_update.estimated_arrival,
        )
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e)
        )
    
    return TripResponse(
        id=trip.id,
        trip_id=trip.trip_id,
        booking_id=trip.booking_id,
        driver_id=trip.driver_id,
        rider_id=trip.rider_id,
        status=trip.status,
        pickup_location={
            'latitude': trip.pickup_latitude,
            'longitude': trip.pickup_longitude,
        },
        dropoff_location={
            'latitude': trip.dropoff_latitude,
            'longitude': trip.dropoff_longitude,
        },
        scheduled_time=trip.scheduled_time,
        started_at=trip.started_at,
        arrived_at=trip.arrived_at,
        pickup_time=trip.pickup_time,
        completed_at=trip.completed_at,
        cancelled_at=trip.cancelled_at,
        estimated_arrival=trip.estimated_arrival,
        distance_meters=trip.distance_meters,
        duration_seconds=trip.duration_seconds,
        created_at=trip.created_at,
        updated_at=trip.updated_at,
    )


@router.get("/driver/{driver_id}", response_model=List[TripResponse])
async def get_driver_trips(
    driver_id: UUID,
    status: Optional[str] = None,
    limit: int = 50,
    offset: int = 0,
    db: AsyncSession = Depends(get_db),
    redis = Depends(get_redis),
):
    """Get trips for a specific driver"""
    trip_service = TripService(db, redis, None)
    trips = await trip_service.get_driver_trips(
        driver_id=driver_id,
        status=status,
        limit=limit,
        offset=offset,
    )
    
    return [
        TripResponse(
            id=trip.id,
            trip_id=trip.trip_id,
            booking_id=trip.booking_id,
            driver_id=trip.driver_id,
            rider_id=trip.rider_id,
            status=trip.status,
            pickup_location={
                'latitude': trip.pickup_latitude,
                'longitude': trip.pickup_longitude,
            },
            dropoff_location={
                'latitude': trip.dropoff_latitude,
                'longitude': trip.dropoff_longitude,
            },
            scheduled_time=trip.scheduled_time,
            started_at=trip.started_at,
            arrived_at=trip.arrived_at,
            pickup_time=trip.pickup_time,
            completed_at=trip.completed_at,
            cancelled_at=trip.cancelled_at,
            estimated_arrival=trip.estimated_arrival,
            distance_meters=trip.distance_meters,
            duration_seconds=trip.duration_seconds,
            created_at=trip.created_at,
            updated_at=trip.updated_at,
        )
        for trip in trips
    ]


@router.get("/rider/{rider_id}", response_model=List[TripResponse])
async def get_rider_trips(
    rider_id: UUID,
    status: Optional[str] = None,
    limit: int = 50,
    offset: int = 0,
    db: AsyncSession = Depends(get_db),
    redis = Depends(get_redis),
):
    """Get trips for a specific rider"""
    trip_service = TripService(db, redis, None)
    trips = await trip_service.get_rider_trips(
        rider_id=rider_id,
        status=status,
        limit=limit,
        offset=offset,
    )
    
    return [
        TripResponse(
            id=trip.id,
            trip_id=trip.trip_id,
            booking_id=trip.booking_id,
            driver_id=trip.driver_id,
            rider_id=trip.rider_id,
            status=trip.status,
            pickup_location={
                'latitude': trip.pickup_latitude,
                'longitude': trip.pickup_longitude,
            },
            dropoff_location={
                'latitude': trip.dropoff_latitude,
                'longitude': trip.dropoff_longitude,
            },
            scheduled_time=trip.scheduled_time,
            started_at=trip.started_at,
            arrived_at=trip.arrived_at,
            pickup_time=trip.pickup_time,
            completed_at=trip.completed_at,
            cancelled_at=trip.cancelled_at,
            estimated_arrival=trip.estimated_arrival,
            distance_meters=trip.distance_meters,
            duration_seconds=trip.duration_seconds,
            created_at=trip.created_at,
            updated_at=trip.updated_at,
        )
        for trip in trips
    ]

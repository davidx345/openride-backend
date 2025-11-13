"""Unit tests for trip service"""
import pytest
from datetime import datetime, timedelta
from decimal import Decimal
from uuid import uuid4

from app.schemas.trip import TripCreate
from app.schemas.location import LocationPoint
from app.services.trip import TripService


@pytest.mark.asyncio
async def test_create_trip(db_session, redis_client, mock_sio):
    """Test creating a trip"""
    trip_service = TripService(db_session, redis_client, mock_sio)
    
    trip_data = TripCreate(
        trip_id=uuid4(),
        booking_id=uuid4(),
        driver_id=uuid4(),
        rider_id=uuid4(),
        pickup_location=LocationPoint(
            latitude=Decimal("37.7749"),
            longitude=Decimal("-122.4194"),
        ),
        dropoff_location=LocationPoint(
            latitude=Decimal("37.8049"),
            longitude=Decimal("-122.4394"),
        ),
        scheduled_time=datetime.utcnow() + timedelta(minutes=10),
    )
    
    trip = await trip_service.create_trip(trip_data)
    
    assert trip.trip_id == trip_data.trip_id
    assert trip.status == "PENDING"


@pytest.mark.asyncio
async def test_trip_status_transitions(db_session, redis_client, mock_sio):
    """Test trip status transitions"""
    trip_service = TripService(db_session, redis_client, mock_sio)
    
    # Create trip
    trip_data = TripCreate(
        trip_id=uuid4(),
        booking_id=uuid4(),
        driver_id=uuid4(),
        rider_id=uuid4(),
        pickup_location=LocationPoint(
            latitude=Decimal("37.7749"),
            longitude=Decimal("-122.4194"),
        ),
        dropoff_location=LocationPoint(
            latitude=Decimal("37.8049"),
            longitude=Decimal("-122.4394"),
        ),
        scheduled_time=datetime.utcnow(),
    )
    trip = await trip_service.create_trip(trip_data)
    
    # PENDING -> EN_ROUTE
    trip = await trip_service.update_trip_status(trip.trip_id, "EN_ROUTE")
    assert trip.status == "EN_ROUTE"
    assert trip.started_at is not None
    
    # EN_ROUTE -> ARRIVED
    trip = await trip_service.update_trip_status(trip.trip_id, "ARRIVED")
    assert trip.status == "ARRIVED"
    assert trip.arrived_at is not None
    
    # ARRIVED -> IN_PROGRESS
    trip = await trip_service.update_trip_status(trip.trip_id, "IN_PROGRESS")
    assert trip.status == "IN_PROGRESS"
    assert trip.pickup_time is not None
    
    # IN_PROGRESS -> COMPLETED
    trip = await trip_service.update_trip_status(trip.trip_id, "COMPLETED")
    assert trip.status == "COMPLETED"
    assert trip.completed_at is not None


@pytest.mark.asyncio
async def test_invalid_status_transition(db_session, redis_client, mock_sio):
    """Test invalid status transition"""
    trip_service = TripService(db_session, redis_client, mock_sio)
    
    # Create trip
    trip_data = TripCreate(
        trip_id=uuid4(),
        booking_id=uuid4(),
        driver_id=uuid4(),
        rider_id=uuid4(),
        pickup_location=LocationPoint(
            latitude=Decimal("37.7749"),
            longitude=Decimal("-122.4194"),
        ),
        dropoff_location=LocationPoint(
            latitude=Decimal("37.8049"),
            longitude=Decimal("-122.4394"),
        ),
        scheduled_time=datetime.utcnow(),
    )
    trip = await trip_service.create_trip(trip_data)
    
    # Try invalid transition: PENDING -> COMPLETED
    with pytest.raises(ValueError):
        await trip_service.update_trip_status(trip.trip_id, "COMPLETED")


@pytest.mark.asyncio
async def test_get_driver_active_trip(db_session, redis_client, mock_sio):
    """Test getting driver's active trip"""
    trip_service = TripService(db_session, redis_client, mock_sio)
    driver_id = uuid4()
    
    # Create active trip
    trip_data = TripCreate(
        trip_id=uuid4(),
        booking_id=uuid4(),
        driver_id=driver_id,
        rider_id=uuid4(),
        pickup_location=LocationPoint(
            latitude=Decimal("37.7749"),
            longitude=Decimal("-122.4194"),
        ),
        dropoff_location=LocationPoint(
            latitude=Decimal("37.8049"),
            longitude=Decimal("-122.4394"),
        ),
        scheduled_time=datetime.utcnow(),
    )
    trip = await trip_service.create_trip(trip_data)
    await trip_service.update_trip_status(trip.trip_id, "EN_ROUTE")
    
    # Get active trip
    active_trip = await trip_service.get_driver_active_trip(driver_id)
    assert active_trip is not None
    assert active_trip.trip_id == trip.trip_id

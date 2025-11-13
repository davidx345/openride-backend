"""Unit tests for location service"""
import pytest
from decimal import Decimal
from uuid import uuid4

from app.services.location import LocationService, RateLimitError


@pytest.mark.asyncio
async def test_update_driver_location(db_session, redis_client, mock_sio):
    """Test updating driver location"""
    location_service = LocationService(db_session, redis_client, mock_sio)
    driver_id = uuid4()
    
    location = await location_service.update_driver_location(
        driver_id=driver_id,
        latitude=Decimal("37.7749"),
        longitude=Decimal("-122.4194"),
        bearing=Decimal("45.5"),
        speed=Decimal("30.0"),
        accuracy=Decimal("5.0"),
    )
    
    assert location.driver_id == driver_id
    assert location.latitude == Decimal("37.7749")
    assert location.longitude == Decimal("-122.4194")
    assert location.status == "ONLINE"


@pytest.mark.asyncio
async def test_location_rate_limiting(db_session, redis_client, mock_sio):
    """Test location update rate limiting"""
    location_service = LocationService(db_session, redis_client, mock_sio)
    driver_id = uuid4()
    
    # First update should succeed
    await location_service.update_driver_location(
        driver_id=driver_id,
        latitude=Decimal("37.7749"),
        longitude=Decimal("-122.4194"),
    )
    
    # Second immediate update should fail
    with pytest.raises(RateLimitError):
        await location_service.update_driver_location(
            driver_id=driver_id,
            latitude=Decimal("37.7750"),
            longitude=Decimal("-122.4195"),
        )


@pytest.mark.asyncio
async def test_find_nearby_drivers(db_session, redis_client, mock_sio):
    """Test finding nearby drivers using PostGIS"""
    location_service = LocationService(db_session, redis_client, mock_sio)
    
    # Create test drivers
    driver1_id = uuid4()
    driver2_id = uuid4()
    
    await location_service.update_driver_location(
        driver_id=driver1_id,
        latitude=Decimal("37.7749"),
        longitude=Decimal("-122.4194"),
    )
    
    await location_service.update_driver_location(
        driver_id=driver2_id,
        latitude=Decimal("37.7850"),
        longitude=Decimal("-122.4094"),
    )
    
    # Find nearby drivers
    nearby = await location_service.find_nearby_drivers(
        latitude=Decimal("37.7749"),
        longitude=Decimal("-122.4194"),
        radius_meters=5000,
        limit=10,
    )
    
    assert len(nearby) >= 1
    assert nearby[0].driver_id == driver1_id


@pytest.mark.asyncio
async def test_driver_status_update(db_session, redis_client, mock_sio):
    """Test updating driver status"""
    location_service = LocationService(db_session, redis_client, mock_sio)
    driver_id = uuid4()
    
    # Create location
    await location_service.update_driver_location(
        driver_id=driver_id,
        latitude=Decimal("37.7749"),
        longitude=Decimal("-122.4194"),
    )
    
    # Update to BUSY
    await location_service.update_driver_status(
        driver_id=driver_id,
        status="BUSY",
    )
    
    # Verify status
    location = await location_service.get_driver_location(driver_id)
    assert location.status == "BUSY"

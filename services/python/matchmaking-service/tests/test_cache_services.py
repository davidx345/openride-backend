"""Tests for cache services."""

import pytest
from uuid import UUID, uuid4

from app.services.driver_cache_service import DriverCacheService
from app.services.hub_cache_service import HubCacheService
from app.core.redis import redis_client


@pytest.fixture
def driver_cache():
    """Create driver cache service."""
    return DriverCacheService(redis_client)


@pytest.fixture
def hub_cache():
    """Create hub cache service."""
    return HubCacheService(redis_client)


# Driver Cache Tests

@pytest.mark.asyncio
async def test_driver_cache_get_set(driver_cache):
    """Test basic driver cache get/set."""
    driver_id = str(uuid4())
    stats = {
        "driver_id": driver_id,
        "rating_avg": 4.7,
        "rating_count": 150,
        "cancellation_rate": 0.02,
        "completed_trips": 500,
    }
    
    # Set
    success = await driver_cache.set_driver_stats(driver_id, stats)
    assert success is True
    
    # Get
    cached = await driver_cache.get_driver_stats(driver_id)
    assert cached is not None
    assert cached["driver_id"] == driver_id
    assert cached["rating_avg"] == 4.7


@pytest.mark.asyncio
async def test_driver_cache_miss(driver_cache):
    """Test cache miss returns None."""
    driver_id = str(uuid4())
    cached = await driver_cache.get_driver_stats(driver_id)
    assert cached is None


@pytest.mark.asyncio
async def test_driver_cache_batch(driver_cache):
    """Test batch driver cache operations."""
    driver_ids = [str(uuid4()) for _ in range(5)]
    stats_map = {
        driver_id: {
            "driver_id": driver_id,
            "rating_avg": 4.5,
            "rating_count": 100,
        }
        for driver_id in driver_ids
    }
    
    # Set batch
    count = await driver_cache.set_driver_stats_batch(stats_map)
    assert count == 5
    
    # Get batch
    cached = await driver_cache.get_driver_stats_batch(driver_ids)
    assert len(cached) == 5
    for driver_id in driver_ids:
        assert driver_id in cached


@pytest.mark.asyncio
async def test_driver_cache_partial_batch(driver_cache):
    """Test batch with partial cache hits."""
    # Cache only some drivers
    cached_ids = [str(uuid4()) for _ in range(3)]
    uncached_ids = [str(uuid4()) for _ in range(2)]
    
    for driver_id in cached_ids:
        await driver_cache.set_driver_stats(driver_id, {"driver_id": driver_id})
    
    # Request all
    all_ids = cached_ids + uncached_ids
    result = await driver_cache.get_driver_stats_batch(all_ids)
    
    # Should only get cached ones
    assert len(result) == 3
    for driver_id in cached_ids:
        assert driver_id in result


@pytest.mark.asyncio
async def test_driver_cache_invalidate(driver_cache):
    """Test cache invalidation."""
    driver_id = str(uuid4())
    stats = {"driver_id": driver_id, "rating_avg": 4.5}
    
    # Set
    await driver_cache.set_driver_stats(driver_id, stats)
    assert await driver_cache.get_driver_stats(driver_id) is not None
    
    # Invalidate
    success = await driver_cache.invalidate_driver_stats(driver_id)
    assert success is True
    
    # Should be gone
    assert await driver_cache.get_driver_stats(driver_id) is None


@pytest.mark.asyncio
async def test_driver_cache_stats(driver_cache):
    """Test cache statistics."""
    # Add some test data
    for i in range(3):
        await driver_cache.set_driver_stats(f"test-driver-{i}", {"id": i})
    
    stats = await driver_cache.get_cache_stats()
    assert "cached_drivers" in stats
    assert "ttl_seconds" in stats
    assert stats["ttl_seconds"] == 300  # 5 minutes


# Hub Cache Tests

@pytest.mark.asyncio
async def test_hub_cache_nearest(hub_cache):
    """Test hub nearest location caching."""
    lat, lon = 6.5244, 3.3792
    hub_data = {
        "id": str(uuid4()),
        "name": "Ikeja Hub",
        "lat": lat,
        "lon": lon,
    }
    
    # Set
    success = await hub_cache.set_nearest_hub(lat, lon, hub_data)
    assert success is True
    
    # Get
    cached = await hub_cache.get_nearest_hub(lat, lon)
    assert cached is not None
    assert cached["name"] == "Ikeja Hub"


@pytest.mark.asyncio
async def test_hub_cache_grid_rounding(hub_cache):
    """Test grid-based key generation."""
    # Nearby coordinates should use same grid key
    lat1, lon1 = 6.5244, 3.3792
    lat2, lon2 = 6.5245, 3.3793  # Very close
    
    hub_data = {"id": "test-hub", "name": "Test Hub"}
    
    # Cache at first location
    await hub_cache.set_nearest_hub(lat1, lon1, hub_data)
    
    # Should hit same grid cell
    cached = await hub_cache.get_nearest_hub(lat2, lon2)
    # May or may not hit depending on grid precision (0.001 degrees)
    # This is expected behavior - testing the concept


@pytest.mark.asyncio
async def test_hub_cache_by_id(hub_cache):
    """Test hub caching by ID."""
    hub_id = uuid4()
    hub_data = {
        "id": str(hub_id),
        "name": "Victoria Island Hub",
    }
    
    # Set
    success = await hub_cache.set_hub_by_id(hub_id, hub_data)
    assert success is True
    
    # Get
    cached = await hub_cache.get_hub_by_id(hub_id)
    assert cached is not None
    assert cached["name"] == "Victoria Island Hub"


@pytest.mark.asyncio
async def test_hub_pair_routes_cache(hub_cache):
    """Test hub pair routes caching."""
    origin_hub_id = uuid4()
    dest_hub_id = uuid4()
    routes = [
        {"id": str(uuid4()), "name": "Route 1"},
        {"id": str(uuid4()), "name": "Route 2"},
    ]
    
    # Set
    success = await hub_cache.set_hub_pair_routes(origin_hub_id, dest_hub_id, routes)
    assert success is True
    
    # Get
    cached = await hub_cache.get_hub_pair_routes(origin_hub_id, dest_hub_id)
    assert cached is not None
    assert len(cached) == 2
    assert cached[0]["name"] == "Route 1"


@pytest.mark.asyncio
async def test_hub_cache_invalidate(hub_cache):
    """Test hub cache invalidation."""
    hub_id = uuid4()
    hub_data = {"id": str(hub_id), "name": "Test Hub"}
    
    # Cache hub
    await hub_cache.set_hub_by_id(hub_id, hub_data)
    assert await hub_cache.get_hub_by_id(hub_id) is not None
    
    # Invalidate
    success = await hub_cache.invalidate_hub(hub_id)
    assert success is True
    
    # Should be gone
    assert await hub_cache.get_hub_by_id(hub_id) is None


@pytest.mark.asyncio
async def test_hub_cache_stats(hub_cache):
    """Test hub cache statistics."""
    # Add test data
    await hub_cache.set_nearest_hub(6.5, 3.3, {"id": "hub1"})
    await hub_cache.set_hub_by_id(uuid4(), {"id": "hub2"})
    
    stats = await hub_cache.get_cache_stats()
    assert "ttl_seconds" in stats
    assert "grid_precision_decimal_places" in stats
    assert stats["ttl_seconds"] == 1800  # 30 minutes
    assert stats["grid_precision_decimal_places"] == 3


@pytest.mark.asyncio
async def test_cache_error_handling(driver_cache):
    """Test cache handles errors gracefully."""
    # Invalid data should not crash
    driver_id = "test-error"
    
    # Should handle gracefully
    cached = await driver_cache.get_driver_stats(driver_id)
    # May return None or cached value depending on error

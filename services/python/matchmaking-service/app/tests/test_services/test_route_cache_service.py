"""Tests for route cache service."""

import pytest
from datetime import time
from uuid import uuid4

from app.core.redis import RedisClient
from app.models.route import Route, RouteStatus
from app.services.route_cache_service import RouteCacheService


@pytest.fixture
def redis_client(mocker):
    """Mock Redis client."""
    client = mocker.Mock(spec=RedisClient)
    client.get_json = mocker.AsyncMock(return_value=None)
    client.set_json = mocker.AsyncMock(return_value=True)
    client.delete = mocker.AsyncMock(return_value=1)
    client.scan_keys = mocker.AsyncMock(return_value=[])
    return client


@pytest.fixture
def route_cache(redis_client):
    """Create route cache service."""
    return RouteCacheService(redis_client)


@pytest.fixture
def sample_routes():
    """Create sample routes."""
    origin_hub = uuid4()
    dest_hub = uuid4()

    routes = [
        Route(
            id=uuid4(),
            driver_id=uuid4(),
            vehicle_id=uuid4(),
            name="Lagos - Ibadan Express",
            departure_time=time(8, 0),
            active_days=[1, 2, 3, 4, 5],
            seats_total=4,
            seats_available=2,
            base_price=5000.0,
            status=RouteStatus.ACTIVE,
            origin_hub_id=origin_hub,
            destination_hub_id=dest_hub,
            currency="NGN",
            estimated_duration_minutes=120,
        ),
        Route(
            id=uuid4(),
            driver_id=uuid4(),
            vehicle_id=uuid4(),
            name="Lagos - Ibadan Standard",
            departure_time=time(9, 0),
            active_days=[1, 2, 3, 4, 5, 6, 7],
            seats_total=4,
            seats_available=4,
            base_price=4500.0,
            status=RouteStatus.ACTIVE,
            origin_hub_id=origin_hub,
            destination_hub_id=dest_hub,
            currency="NGN",
            estimated_duration_minutes=140,
        ),
    ]
    return routes


class TestRouteCacheService:
    """Test route cache service."""

    @pytest.mark.asyncio
    async def test_generate_cache_key_deterministic(self, route_cache):
        """Test cache key generation is deterministic."""
        origin_hub = uuid4()
        dest_hub = uuid4()
        departure_time = time(8, 0)

        key1 = route_cache._generate_cache_key(
            origin_hub, dest_hub, departure_time, True
        )
        key2 = route_cache._generate_cache_key(
            origin_hub, dest_hub, departure_time, True
        )

        assert key1 == key2
        assert key1.startswith("route_cache:query:")

    @pytest.mark.asyncio
    async def test_generate_cache_key_different_params(self, route_cache):
        """Test different parameters generate different keys."""
        origin_hub1 = uuid4()
        origin_hub2 = uuid4()
        dest_hub = uuid4()
        departure_time = time(8, 0)

        key1 = route_cache._generate_cache_key(
            origin_hub1, dest_hub, departure_time, True
        )
        key2 = route_cache._generate_cache_key(
            origin_hub2, dest_hub, departure_time, True
        )

        assert key1 != key2

    @pytest.mark.asyncio
    async def test_get_cached_routes_cache_miss(self, route_cache, redis_client):
        """Test cache miss returns None."""
        redis_client.get_json.return_value = None

        result = await route_cache.get_cached_routes(
            origin_hub_id=uuid4(),
            destination_hub_id=uuid4(),
            departure_time=time(8, 0),
        )

        assert result is None
        redis_client.get_json.assert_called_once()

    @pytest.mark.asyncio
    async def test_get_cached_routes_cache_hit(self, route_cache, redis_client):
        """Test cache hit returns route data."""
        cached_data = [
            {"id": str(uuid4()), "name": "Test Route 1"},
            {"id": str(uuid4()), "name": "Test Route 2"},
        ]
        redis_client.get_json.return_value = cached_data

        result = await route_cache.get_cached_routes(
            origin_hub_id=uuid4(),
            destination_hub_id=uuid4(),
            departure_time=time(8, 0),
        )

        assert result == cached_data
        redis_client.get_json.assert_called_once()

    @pytest.mark.asyncio
    async def test_cache_routes_success(
        self, route_cache, redis_client, sample_routes
    ):
        """Test caching routes successfully."""
        origin_hub = sample_routes[0].origin_hub_id
        dest_hub = sample_routes[0].destination_hub_id

        redis_client.set_json.return_value = True

        success = await route_cache.cache_routes(
            routes=sample_routes,
            origin_hub_id=origin_hub,
            destination_hub_id=dest_hub,
            departure_time=time(8, 0),
        )

        assert success is True
        redis_client.set_json.assert_called_once()

        # Verify serialized data structure
        call_args = redis_client.set_json.call_args
        cached_data = call_args[0][1]
        assert len(cached_data) == 2
        assert cached_data[0]["name"] == "Lagos - Ibadan Express"
        assert cached_data[0]["seats_available"] == 2

    @pytest.mark.asyncio
    async def test_cache_routes_with_custom_ttl(
        self, route_cache, redis_client, sample_routes
    ):
        """Test caching with custom TTL."""
        origin_hub = sample_routes[0].origin_hub_id
        dest_hub = sample_routes[0].destination_hub_id

        await route_cache.cache_routes(
            routes=sample_routes,
            origin_hub_id=origin_hub,
            destination_hub_id=dest_hub,
            departure_time=time(8, 0),
            ttl=60,
        )

        call_args = redis_client.set_json.call_args
        assert call_args[0][2] == 60  # TTL parameter

    @pytest.mark.asyncio
    async def test_invalidate_route(self, route_cache, redis_client):
        """Test route invalidation."""
        route_id = uuid4()
        redis_client.scan_keys.return_value = [
            "route_cache:query:abc123",
            "route_cache:query:def456",
        ]
        redis_client.delete.return_value = 3

        result = await route_cache.invalidate_route(route_id)

        assert result is True
        # Should delete route key and scan for query keys
        assert redis_client.delete.call_count == 2

    @pytest.mark.asyncio
    async def test_invalidate_hub_routes(self, route_cache, redis_client):
        """Test hub routes invalidation."""
        hub_id = uuid4()
        redis_client.scan_keys.return_value = [
            "route_cache:query:abc123",
            "route_cache:query:def456",
            "route_cache:query:ghi789",
        ]
        redis_client.delete.return_value = 3

        deleted = await route_cache.invalidate_hub_routes(hub_id)

        assert deleted == 3
        redis_client.scan_keys.assert_called_once()
        redis_client.delete.assert_called_once()

    @pytest.mark.asyncio
    async def test_clear_all_caches(self, route_cache, redis_client):
        """Test clearing all caches."""
        redis_client.scan_keys.return_value = [
            "route_cache:query:abc123",
            "route_cache:route:route-id-1",
            "route_cache:query:def456",
        ]
        redis_client.delete.return_value = 3

        deleted = await route_cache.clear_all_caches()

        assert deleted == 3
        redis_client.scan_keys.assert_called_with("route_cache:*")

    @pytest.mark.asyncio
    async def test_get_cache_stats(self, route_cache, redis_client):
        """Test cache statistics."""
        redis_client.scan_keys.side_effect = [
            ["query:1", "query:2"],  # Query caches
            ["route:1"],  # Route caches
        ]

        stats = await route_cache.get_cache_stats()

        assert stats["query_caches"] == 2
        assert stats["route_caches"] == 1
        assert stats["total_keys"] == 3
        assert stats["cache_prefix"] == "route_cache"

    @pytest.mark.asyncio
    async def test_generate_route_key(self, route_cache):
        """Test individual route key generation."""
        route_id = uuid4()
        key = route_cache._generate_route_key(route_id)

        assert key == f"route_cache:route:{route_id}"

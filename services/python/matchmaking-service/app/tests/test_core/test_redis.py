"""Tests for Redis client wrapper."""

import json
import pytest
from redis.exceptions import RedisError

from app.core.redis import RedisClient


@pytest.fixture
def redis_client():
    """Create Redis client instance."""
    return RedisClient()


@pytest.fixture
def mock_redis(mocker):
    """Mock Redis connection."""
    mock = mocker.AsyncMock()
    mock.ping = mocker.AsyncMock()
    mock.get = mocker.AsyncMock()
    mock.set = mocker.AsyncMock()
    mock.setex = mocker.AsyncMock()
    mock.delete = mocker.AsyncMock()
    mock.exists = mocker.AsyncMock()
    mock.expire = mocker.AsyncMock()
    mock.scan = mocker.AsyncMock()
    mock.close = mocker.AsyncMock()
    return mock


class TestRedisClient:
    """Test Redis client wrapper."""

    @pytest.mark.asyncio
    async def test_connect_success(self, redis_client, mocker, mock_redis):
        """Test successful Redis connection."""
        mocker.patch("app.core.redis.from_url", return_value=mock_redis)

        await redis_client.connect()

        assert redis_client._redis is not None
        mock_redis.ping.assert_called_once()

    @pytest.mark.asyncio
    async def test_connect_failure(self, redis_client, mocker):
        """Test Redis connection failure."""
        mock_from_url = mocker.patch("app.core.redis.from_url")
        mock_redis = mocker.AsyncMock()
        mock_redis.ping = mocker.AsyncMock(side_effect=RedisError("Connection failed"))
        mock_from_url.return_value = mock_redis

        with pytest.raises(RedisError):
            await redis_client.connect()

    @pytest.mark.asyncio
    async def test_disconnect(self, redis_client, mock_redis):
        """Test Redis disconnection."""
        redis_client._redis = mock_redis

        await redis_client.disconnect()

        mock_redis.close.assert_called_once()

    @pytest.mark.asyncio
    async def test_client_property_not_connected(self, redis_client):
        """Test client property raises when not connected."""
        with pytest.raises(RuntimeError, match="not connected"):
            _ = redis_client.client

    @pytest.mark.asyncio
    async def test_client_property_connected(self, redis_client, mock_redis):
        """Test client property returns Redis instance."""
        redis_client._redis = mock_redis
        assert redis_client.client == mock_redis

    @pytest.mark.asyncio
    async def test_get_success(self, redis_client, mock_redis):
        """Test GET operation success."""
        redis_client._redis = mock_redis
        mock_redis.get.return_value = "test_value"

        result = await redis_client.get("test_key")

        assert result == "test_value"
        mock_redis.get.assert_called_once_with("test_key")

    @pytest.mark.asyncio
    async def test_get_not_found(self, redis_client, mock_redis):
        """Test GET operation key not found."""
        redis_client._redis = mock_redis
        mock_redis.get.return_value = None

        result = await redis_client.get("missing_key")

        assert result is None

    @pytest.mark.asyncio
    async def test_get_error(self, redis_client, mock_redis):
        """Test GET operation error handling."""
        redis_client._redis = mock_redis
        mock_redis.get.side_effect = RedisError("GET failed")

        result = await redis_client.get("test_key")

        assert result is None

    @pytest.mark.asyncio
    async def test_set_without_ttl(self, redis_client, mock_redis):
        """Test SET operation without TTL."""
        redis_client._redis = mock_redis

        success = await redis_client.set("test_key", "test_value")

        assert success is True
        mock_redis.set.assert_called_once_with("test_key", "test_value")

    @pytest.mark.asyncio
    async def test_set_with_ttl(self, redis_client, mock_redis):
        """Test SET operation with TTL."""
        redis_client._redis = mock_redis

        success = await redis_client.set("test_key", "test_value", ttl=300)

        assert success is True
        mock_redis.setex.assert_called_once_with("test_key", 300, "test_value")

    @pytest.mark.asyncio
    async def test_set_error(self, redis_client, mock_redis):
        """Test SET operation error handling."""
        redis_client._redis = mock_redis
        mock_redis.set.side_effect = RedisError("SET failed")

        success = await redis_client.set("test_key", "test_value")

        assert success is False

    @pytest.mark.asyncio
    async def test_delete_success(self, redis_client, mock_redis):
        """Test DELETE operation success."""
        redis_client._redis = mock_redis
        mock_redis.delete.return_value = 2

        deleted = await redis_client.delete("key1", "key2")

        assert deleted == 2
        mock_redis.delete.assert_called_once_with("key1", "key2")

    @pytest.mark.asyncio
    async def test_delete_error(self, redis_client, mock_redis):
        """Test DELETE operation error handling."""
        redis_client._redis = mock_redis
        mock_redis.delete.side_effect = RedisError("DELETE failed")

        deleted = await redis_client.delete("test_key")

        assert deleted == 0

    @pytest.mark.asyncio
    async def test_get_json_success(self, redis_client, mock_redis):
        """Test GET JSON operation success."""
        redis_client._redis = mock_redis
        test_data = {"key": "value", "number": 42}
        mock_redis.get.return_value = json.dumps(test_data)

        result = await redis_client.get_json("test_key")

        assert result == test_data

    @pytest.mark.asyncio
    async def test_get_json_invalid(self, redis_client, mock_redis):
        """Test GET JSON operation with invalid JSON."""
        redis_client._redis = mock_redis
        mock_redis.get.return_value = "invalid json"

        result = await redis_client.get_json("test_key")

        assert result is None

    @pytest.mark.asyncio
    async def test_set_json_success(self, redis_client, mock_redis):
        """Test SET JSON operation success."""
        redis_client._redis = mock_redis
        test_data = {"key": "value", "list": [1, 2, 3]}

        success = await redis_client.set_json("test_key", test_data, ttl=60)

        assert success is True
        mock_redis.setex.assert_called_once()
        # Verify JSON serialization
        call_args = mock_redis.setex.call_args[0]
        assert json.loads(call_args[2]) == test_data

    @pytest.mark.asyncio
    async def test_set_json_invalid(self, redis_client, mock_redis):
        """Test SET JSON operation with non-serializable data."""
        redis_client._redis = mock_redis

        # Create non-serializable object
        class CustomClass:
            pass

        success = await redis_client.set_json("test_key", CustomClass())

        assert success is False

    @pytest.mark.asyncio
    async def test_exists_true(self, redis_client, mock_redis):
        """Test EXISTS operation returns true."""
        redis_client._redis = mock_redis
        mock_redis.exists.return_value = 1

        result = await redis_client.exists("test_key")

        assert result is True

    @pytest.mark.asyncio
    async def test_exists_false(self, redis_client, mock_redis):
        """Test EXISTS operation returns false."""
        redis_client._redis = mock_redis
        mock_redis.exists.return_value = 0

        result = await redis_client.exists("test_key")

        assert result is False

    @pytest.mark.asyncio
    async def test_expire_success(self, redis_client, mock_redis):
        """Test EXPIRE operation success."""
        redis_client._redis = mock_redis
        mock_redis.expire.return_value = True

        result = await redis_client.expire("test_key", 300)

        assert result is True
        mock_redis.expire.assert_called_once_with("test_key", 300)

    @pytest.mark.asyncio
    async def test_scan_keys_success(self, redis_client, mock_redis):
        """Test SCAN operation success."""
        redis_client._redis = mock_redis
        # Simulate two scan iterations
        mock_redis.scan.side_effect = [
            (5, ["key1", "key2"]),  # First iteration
            (0, ["key3"]),  # Second iteration (cursor=0 means done)
        ]

        keys = await redis_client.scan_keys("test:*")

        assert keys == ["key1", "key2", "key3"]
        assert mock_redis.scan.call_count == 2

    @pytest.mark.asyncio
    async def test_scan_keys_error(self, redis_client, mock_redis):
        """Test SCAN operation error handling."""
        redis_client._redis = mock_redis
        mock_redis.scan.side_effect = RedisError("SCAN failed")

        keys = await redis_client.scan_keys("test:*")

        assert keys == []

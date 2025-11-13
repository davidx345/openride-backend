"""Test fixtures and configuration."""

import pytest
from unittest.mock import MagicMock


@pytest.fixture
def mock_clickhouse():
    """Mock ClickHouse client."""
    mock = MagicMock()
    mock.query.return_value = MagicMock(row_count=0, first_row=None)
    mock.ping.return_value = True
    return mock


@pytest.fixture
async def mock_redis():
    """Mock Redis client."""
    mock = MagicMock()
    mock.ping.return_value = True
    mock.get.return_value = None
    mock.set.return_value = True
    return mock


@pytest.fixture
def mock_kafka_consumer():
    """Mock Kafka consumer."""
    mock = MagicMock()
    mock.subscribe.return_value = None
    mock.poll.return_value = None
    return mock

"""Test suite for HubRepository."""

import pytest
from decimal import Decimal
from unittest.mock import AsyncMock, MagicMock

from app.models.hub import Hub
from app.repositories.hub_repository import HubRepository


@pytest.fixture
def mock_db():
    """Mock database session."""
    return AsyncMock()


@pytest.fixture
def hub_repository(mock_db):
    """Hub repository with mocked database."""
    return HubRepository(mock_db)


@pytest.fixture
def sample_hub():
    """Sample hub for testing."""
    hub = Hub(
        name="Victoria Island Hub",
        lat=Decimal("6.4281"),
        lon=Decimal("3.4219"),
        area_id="VI",
        zone="Island",
        is_active=True
    )
    return hub


class TestHubRepository:
    """Test cases for HubRepository."""

    @pytest.mark.asyncio
    async def test_get_by_id_found(self, hub_repository, mock_db, sample_hub):
        """Test getting hub by ID when found."""
        # Setup
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = sample_hub
        mock_db.execute.return_value = mock_result

        # Execute
        result = await hub_repository.get_by_id(sample_hub.id)

        # Assert
        assert result == sample_hub
        mock_db.execute.assert_called_once()

    @pytest.mark.asyncio
    async def test_get_by_id_not_found(self, hub_repository, mock_db):
        """Test getting hub by ID when not found."""
        # Setup
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = None
        mock_db.execute.return_value = mock_result

        # Execute
        from uuid import uuid4
        result = await hub_repository.get_by_id(uuid4())

        # Assert
        assert result is None
        mock_db.execute.assert_called_once()

    @pytest.mark.asyncio
    async def test_get_all_active(self, hub_repository, mock_db, sample_hub):
        """Test getting all active hubs."""
        # Setup
        hubs = [sample_hub]
        mock_result = MagicMock()
        mock_scalars = MagicMock()
        mock_scalars.all.return_value = hubs
        mock_result.scalars.return_value = mock_scalars
        mock_db.execute.return_value = mock_result

        # Execute
        result = await hub_repository.get_all_active()

        # Assert
        assert result == hubs
        mock_db.execute.assert_called_once()

    @pytest.mark.asyncio
    async def test_get_by_area(self, hub_repository, mock_db, sample_hub):
        """Test getting hubs by area."""
        # Setup
        hubs = [sample_hub]
        mock_result = MagicMock()
        mock_scalars = MagicMock()
        mock_scalars.all.return_value = hubs
        mock_result.scalars.return_value = mock_scalars
        mock_db.execute.return_value = mock_result

        # Execute
        result = await hub_repository.get_by_area("VI")

        # Assert
        assert result == hubs
        mock_db.execute.assert_called_once()

    @pytest.mark.asyncio
    async def test_create(self, hub_repository, mock_db, sample_hub):
        """Test creating a hub."""
        # Execute
        result = await hub_repository.create(sample_hub)

        # Assert
        assert result == sample_hub
        mock_db.add.assert_called_once_with(sample_hub)
        mock_db.commit.assert_called_once()
        mock_db.refresh.assert_called_once_with(sample_hub)

    @pytest.mark.asyncio
    async def test_deactivate_found(self, hub_repository, mock_db, sample_hub):
        """Test deactivating a hub."""
        # Setup
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = sample_hub
        mock_db.execute.return_value = mock_result

        # Execute
        result = await hub_repository.deactivate(sample_hub.id)

        # Assert
        assert result is True
        assert sample_hub.is_active is False
        mock_db.commit.assert_called_once()

    @pytest.mark.asyncio
    async def test_deactivate_not_found(self, hub_repository, mock_db):
        """Test deactivating a hub that doesn't exist."""
        # Setup
        mock_result = MagicMock()
        mock_result.scalar_one_or_none.return_value = None
        mock_db.execute.return_value = mock_result

        # Execute
        from uuid import uuid4
        result = await hub_repository.deactivate(uuid4())

        # Assert
        assert result is False
        mock_db.commit.assert_not_called()

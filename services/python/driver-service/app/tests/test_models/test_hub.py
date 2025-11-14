"""Test suite for Hub model."""

import pytest
from decimal import Decimal

from app.models.hub import Hub


class TestHubModel:
    """Test cases for Hub model."""

    def test_hub_creation(self):
        """Test creating a hub instance."""
        hub = Hub(
            name="Victoria Island Hub",
            lat=Decimal("6.4281"),
            lon=Decimal("3.4219"),
            area_id="VI",
            zone="Island",
            is_active=True,
            address="Ahmadu Bello Way, Victoria Island",
            landmark="Eko Hotel"
        )
        
        assert hub.name == "Victoria Island Hub"
        assert hub.lat == Decimal("6.4281")
        assert hub.lon == Decimal("3.4219")
        assert hub.area_id == "VI"
        assert hub.zone == "Island"
        assert hub.is_active is True
        assert hub.address == "Ahmadu Bello Way, Victoria Island"
        assert hub.landmark == "Eko Hotel"

    def test_hub_repr(self):
        """Test hub string representation."""
        hub = Hub(name="Test Hub", area_id="Test")
        assert "Test Hub" in repr(hub)
        assert "Test" in repr(hub)

    def test_hub_defaults(self):
        """Test hub default values."""
        hub = Hub(
            name="Minimal Hub",
            lat=Decimal("6.5"),
            lon=Decimal("3.5")
        )
        
        assert hub.is_active is True
        assert hub.area_id is None
        assert hub.zone is None
        assert hub.address is None
        assert hub.landmark is None

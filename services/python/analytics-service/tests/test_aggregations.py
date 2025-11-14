"""Test aggregation service methods."""

import pytest
from datetime import datetime, timedelta
from decimal import Decimal
from unittest.mock import Mock, patch

from app.services.aggregations import MetricsAggregationService


@pytest.fixture
def mock_clickhouse():
    """Mock ClickHouse client."""
    return Mock()


@pytest.fixture
def service(mock_clickhouse):
    """Create aggregation service instance."""
    return MetricsAggregationService(mock_clickhouse)


class TestUserMetrics:
    """Test user metrics aggregation."""

    @pytest.mark.asyncio
    async def test_get_user_metrics_success(self, service, mock_clickhouse):
        """Test successful user metrics retrieval."""
        # Arrange
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        
        mock_result = Mock()
        mock_result.row_count = 1
        mock_result.first_row = [100, 80, 25]  # registrations, kyc, driver_upgrades
        mock_clickhouse.query.return_value = mock_result
        
        # Mock active users calculations
        service._calculate_active_users = Mock(side_effect=[150, 400, 800])
        service._get_total_users = Mock(return_value=5000)
        
        # Act
        result = await service.get_user_metrics(start_date, end_date)
        
        # Assert
        assert result.new_registrations == 100
        assert result.dau == 150
        assert result.wau == 400
        assert result.mau == 800
        assert result.total_users == 5000
        assert result.kyc_completion_rate == 80.0
        assert result.driver_activation_rate == 25.0

    @pytest.mark.asyncio
    async def test_get_user_metrics_no_data(self, service, mock_clickhouse):
        """Test user metrics with no data."""
        # Arrange
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        
        mock_result = Mock()
        mock_result.row_count = 0
        mock_clickhouse.query.return_value = mock_result
        
        service._calculate_active_users = Mock(return_value=0)
        service._get_total_users = Mock(return_value=0)
        
        # Act
        result = await service.get_user_metrics(start_date, end_date)
        
        # Assert
        assert result.new_registrations == 0
        assert result.kyc_completion_rate == 0.0


class TestBookingMetrics:
    """Test booking metrics aggregation."""

    @pytest.mark.asyncio
    async def test_get_booking_metrics_success(self, service, mock_clickhouse):
        """Test successful booking metrics retrieval."""
        # Arrange
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        
        mock_result = Mock()
        mock_result.row_count = 1
        mock_result.first_row = [200, 180, 10, 170, 270000, 1500]
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_booking_metrics(start_date, end_date)
        
        # Assert
        assert result.bookings_created == 200
        assert result.bookings_confirmed == 180
        assert result.bookings_cancelled == 10
        assert result.bookings_completed == 170
        assert result.conversion_rate == 90.0
        assert result.cancellation_rate == 5.0
        assert result.avg_booking_value == Decimal("1500")
        assert result.total_booking_value == Decimal("270000")


class TestPaymentMetrics:
    """Test payment metrics aggregation."""

    @pytest.mark.asyncio
    async def test_get_payment_metrics_success(self, service, mock_clickhouse):
        """Test successful payment metrics retrieval."""
        # Arrange
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        
        mock_result = Mock()
        mock_result.row_count = 1
        mock_result.first_row = [200, 195, 5, 2, 292500, 2500]
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_payment_metrics(start_date, end_date)
        
        # Assert
        assert result.payments_initiated == 200
        assert result.payments_success == 195
        assert result.payments_failed == 5
        assert result.payments_refunded == 2
        assert result.success_rate == 97.5
        assert result.avg_processing_time_ms == 2500.0
        assert result.total_revenue == Decimal("292500")


class TestTripMetrics:
    """Test trip metrics aggregation."""

    @pytest.mark.asyncio
    async def test_get_trip_metrics_success(self, service, mock_clickhouse):
        """Test successful trip metrics retrieval."""
        # Arrange
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        
        mock_result = Mock()
        mock_result.row_count = 1
        mock_result.first_row = [150, 145, 5, 45, 25.5, 125000, 22000, 140, 42]
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_trip_metrics(start_date, end_date)
        
        # Assert
        assert result.trips_started == 150
        assert result.trips_completed == 145
        assert result.trips_cancelled == 5
        assert result.completion_rate == pytest.approx(96.67, 0.01)
        assert result.avg_duration_minutes == 45.0
        assert result.avg_distance_km == 25.5
        assert result.on_time_performance == pytest.approx(96.55, 0.01)
        assert result.total_driver_earnings == Decimal("125000")
        assert result.total_platform_commission == Decimal("22000")
        assert result.active_drivers == 42


class TestRealtimeMetrics:
    """Test realtime metrics aggregation."""

    @pytest.mark.asyncio
    async def test_get_realtime_metrics_success(self, service, mock_clickhouse):
        """Test successful realtime metrics retrieval."""
        # Arrange
        mock_result = Mock()
        mock_result.row_count = 1
        mock_result.first_row = [120, 45, 67500]
        
        active_trips_result = Mock()
        active_trips_result.row_count = 1
        active_trips_result.first_row = [8]
        
        active_drivers_result = Mock()
        active_drivers_result.row_count = 1
        active_drivers_result.first_row = [15]
        
        mock_clickhouse.query.side_effect = [
            mock_result,
            active_trips_result,
            active_drivers_result
        ]
        
        # Act
        result = await service.get_realtime_metrics()
        
        # Assert
        assert result.active_users == 120
        assert result.active_drivers == 15
        assert result.active_trips == 8
        assert result.bookings_last_hour == 45
        assert result.revenue_last_hour == Decimal("67500")
        assert result.system_health == "healthy"


class TestDriverMetrics:
    """Test driver metrics aggregation."""

    @pytest.mark.asyncio
    async def test_get_driver_metrics_success(self, service, mock_clickhouse):
        """Test successful driver metrics retrieval."""
        # Arrange
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        
        mock_result = Mock()
        mock_result.result_rows = [
            ("driver-1", 50, 75000, 1500, 1250.5, 4.8),
            ("driver-2", 45, 67500, 1500, 1125.0, 4.9),
        ]
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_driver_metrics(start_date, end_date, limit=10)
        
        # Assert
        assert len(result) == 2
        assert result[0].driver_id == "driver-1"
        assert result[0].total_trips == 50
        assert result[0].total_earnings == Decimal("75000")
        assert result[0].avg_earnings_per_trip == Decimal("1500")
        assert result[0].total_distance_km == 1250.5
        assert result[0].avg_rating == 4.8


class TestRouteMetrics:
    """Test route metrics aggregation."""

    @pytest.mark.asyncio
    async def test_get_route_metrics_success(self, service, mock_clickhouse):
        """Test successful route metrics retrieval."""
        # Arrange
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        
        mock_result = Mock()
        mock_result.result_rows = [
            ("route-1", "Lekki → VI", "Lekki", "Victoria Island", "driver-1", 120, 180000, 0.85, 4),
            ("route-2", "Ajah → Ikoyi", "Ajah", "Ikoyi", "driver-2", 95, 142500, 0.80, 4),
        ]
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_route_metrics(start_date, end_date, limit=10)
        
        # Assert
        assert len(result) == 2
        assert result[0].route_id == "route-1"
        assert result[0].route_name == "Lekki → VI"
        assert result[0].origin_city == "Lekki"
        assert result[0].destination_city == "Victoria Island"
        assert result[0].total_bookings == 120
        assert result[0].total_revenue == Decimal("180000")
        assert result[0].avg_occupancy_rate == 85.0
        assert result[0].seats_total == 4


class TestGeographicMetrics:
    """Test geographic metrics aggregation."""

    @pytest.mark.asyncio
    async def test_get_geographic_metrics_success(self, service, mock_clickhouse):
        """Test successful geographic metrics retrieval."""
        # Arrange
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        
        mock_result = Mock()
        mock_result.result_rows = [
            ("Lagos", "Lekki", 450),
            ("Lagos", "Victoria Island", 380),
            ("Lagos", "Ikeja", 320),
        ]
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_geographic_metrics(start_date, end_date, metric_type="bookings")
        
        # Assert
        assert len(result) == 3
        assert result[0].state == "Lagos"
        assert result[0].city == "Lekki"
        assert result[0].value == 450.0

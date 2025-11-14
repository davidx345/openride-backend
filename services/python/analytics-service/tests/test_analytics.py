"""Test analytics service methods."""

import pytest
from datetime import datetime, timedelta
from decimal import Decimal
from unittest.mock import Mock, patch

from app.services.analytics import AnalyticsService


@pytest.fixture
def mock_clickhouse():
    """Mock ClickHouse client."""
    return Mock()


@pytest.fixture
def service(mock_clickhouse):
    """Create analytics service instance."""
    return AnalyticsService(mock_clickhouse)


class TestFunnelAnalysis:
    """Test funnel analysis functionality."""

    @pytest.mark.asyncio
    async def test_get_funnel_analysis_complete_funnel(self, service, mock_clickhouse):
        """Test funnel analysis with complete conversion data."""
        # Arrange
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        
        mock_result = Mock()
        mock_result.row_count = 1
        mock_result.first_row = [1000, 850, 700, 650, 600]  # Each funnel step
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_funnel_analysis(start_date, end_date)
        
        # Assert
        assert result.total_users_entered == 1000
        assert result.total_users_converted == 600
        assert result.overall_conversion_rate == 60.0
        assert len(result.funnel_steps) == 5
        
        # Check first step
        assert result.funnel_steps[0].step_name == "User Registration"
        assert result.funnel_steps[0].count == 1000
        assert result.funnel_steps[0].conversion_rate == 100.0
        
        # Check last step
        assert result.funnel_steps[4].step_name == "Trip Completed"
        assert result.funnel_steps[4].count == 600
        assert result.funnel_steps[4].conversion_rate == 60.0

    @pytest.mark.asyncio
    async def test_get_funnel_analysis_zero_conversions(self, service, mock_clickhouse):
        """Test funnel analysis with zero conversions."""
        # Arrange
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        
        mock_result = Mock()
        mock_result.row_count = 1
        mock_result.first_row = [0, 0, 0, 0, 0]
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_funnel_analysis(start_date, end_date)
        
        # Assert
        assert result.total_users_entered == 0
        assert result.total_users_converted == 0
        assert result.overall_conversion_rate == 0.0


class TestCohortAnalysis:
    """Test cohort analysis functionality."""

    @pytest.mark.asyncio
    async def test_get_cohort_analysis_weekly(self, service, mock_clickhouse):
        """Test weekly cohort analysis."""
        # Arrange
        cohort_type = "weekly"
        start_date = datetime(2024, 1, 1)
        periods = 4
        
        mock_result = Mock()
        mock_result.result_rows = [
            (datetime(2024, 1, 1), datetime(2024, 1, 1), 100, 100),  # Week 0
            (datetime(2024, 1, 1), datetime(2024, 1, 8), 45, 100),   # Week 1
            (datetime(2024, 1, 1), datetime(2024, 1, 15), 30, 100),  # Week 2
            (datetime(2024, 1, 8), datetime(2024, 1, 8), 120, 120),  # New cohort
            (datetime(2024, 1, 8), datetime(2024, 1, 15), 60, 120),
        ]
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_cohort_analysis(cohort_type, start_date, periods)
        
        # Assert
        assert result.cohort_type == "weekly"
        assert result.periods_analyzed == 4
        assert len(result.cohorts) >= 1
        
        # Check first cohort
        cohort_1 = result.cohorts[0]
        assert cohort_1.cohort_size == 100
        assert "period_0" in cohort_1.retention_by_period
        assert cohort_1.retention_by_period["period_0"] == 100.0

    @pytest.mark.asyncio
    async def test_get_cohort_analysis_monthly(self, service, mock_clickhouse):
        """Test monthly cohort analysis."""
        # Arrange
        cohort_type = "monthly"
        start_date = datetime(2024, 1, 1)
        periods = 3
        
        mock_result = Mock()
        mock_result.result_rows = [
            (datetime(2024, 1, 1), datetime(2024, 1, 1), 500, 500),
            (datetime(2024, 1, 1), datetime(2024, 2, 1), 200, 500),
        ]
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_cohort_analysis(cohort_type, start_date, periods)
        
        # Assert
        assert result.cohort_type == "monthly"
        assert result.periods_analyzed == 3


class TestRetentionAnalysis:
    """Test retention analysis functionality."""

    @pytest.mark.asyncio
    async def test_get_retention_analysis_all_users(self, service, mock_clickhouse):
        """Test retention analysis for all users."""
        # Arrange
        user_type = "all"
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        
        mock_result = Mock()
        mock_result.row_count = 1
        mock_result.first_row = [1000, 450, 300, 150]  # total, d1, d7, d30
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_retention_analysis(user_type, start_date, end_date)
        
        # Assert
        assert result.user_type == "all"
        assert result.total_users == 1000
        assert result.d1_retained_users == 450
        assert result.d7_retained_users == 300
        assert result.d30_retained_users == 150
        assert result.d1_retention_rate == 45.0
        assert result.d7_retention_rate == 30.0
        assert result.d30_retention_rate == 15.0

    @pytest.mark.asyncio
    async def test_get_retention_analysis_drivers_only(self, service, mock_clickhouse):
        """Test retention analysis for drivers only."""
        # Arrange
        user_type = "driver"
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        
        mock_result = Mock()
        mock_result.row_count = 1
        mock_result.first_row = [200, 120, 90, 50]
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_retention_analysis(user_type, start_date, end_date)
        
        # Assert
        assert result.user_type == "driver"
        assert result.total_users == 200
        assert result.d1_retention_rate == 60.0


class TestGeographicDistribution:
    """Test geographic distribution functionality."""

    @pytest.mark.asyncio
    async def test_get_geographic_distribution_bookings(self, service, mock_clickhouse):
        """Test geographic distribution for bookings."""
        # Arrange
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        metric = "bookings"
        
        mock_result = Mock()
        mock_result.result_rows = [
            ("Lagos", "Lekki", 500),
            ("Lagos", "Victoria Island", 450),
            ("Abuja", "Garki", 200),
        ]
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_geographic_distribution(start_date, end_date, metric)
        
        # Assert
        assert result.metric_type == "bookings"
        assert len(result.data_points) == 3
        assert result.total_value == 1150.0
        assert result.data_points[0].state == "Lagos"
        assert result.data_points[0].city == "Lekki"
        assert result.data_points[0].value == 500.0

    @pytest.mark.asyncio
    async def test_get_geographic_distribution_revenue(self, service, mock_clickhouse):
        """Test geographic distribution for revenue."""
        # Arrange
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        metric = "revenue"
        
        mock_result = Mock()
        mock_result.result_rows = [
            ("Lagos", "Lekki", 750000),
            ("Lagos", "Ikeja", 500000),
        ]
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_geographic_distribution(start_date, end_date, metric)
        
        # Assert
        assert result.metric_type == "revenue"
        assert result.total_value == 1250000.0


class TestTimeSeriesTrends:
    """Test time series trends functionality."""

    @pytest.mark.asyncio
    async def test_get_time_series_trends_daily(self, service, mock_clickhouse):
        """Test daily time series trends."""
        # Arrange
        metric = "bookings"
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 7)
        granularity = "daily"
        
        mock_result = Mock()
        mock_result.result_rows = [
            (datetime(2024, 1, 1).date(), 45),
            (datetime(2024, 1, 2).date(), 52),
            (datetime(2024, 1, 3).date(), 48),
        ]
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_time_series_trends(metric, start_date, end_date, granularity)
        
        # Assert
        assert result["metric"] == "bookings"
        assert result["granularity"] == "daily"
        assert len(result["data_points"]) == 3

    @pytest.mark.asyncio
    async def test_get_time_series_trends_revenue(self, service, mock_clickhouse):
        """Test revenue time series trends."""
        # Arrange
        metric = "revenue"
        start_date = datetime(2024, 1, 1)
        end_date = datetime(2024, 1, 31)
        granularity = "weekly"
        
        mock_result = Mock()
        mock_result.result_rows = [
            (datetime(2024, 1, 1), 125000),
            (datetime(2024, 1, 8), 145000),
        ]
        mock_clickhouse.query.return_value = mock_result
        
        # Act
        result = await service.get_time_series_trends(metric, start_date, end_date, granularity)
        
        # Assert
        assert result["metric"] == "revenue"
        assert result["granularity"] == "weekly"

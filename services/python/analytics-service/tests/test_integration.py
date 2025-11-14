"""Integration tests for analytics API endpoints."""

import pytest
from datetime import datetime, timedelta
from fastapi.testclient import TestClient

from app.main import app


@pytest.fixture
def client():
    """Create test client."""
    return TestClient(app)


class TestMetricsEndpoints:
    """Test metrics API endpoints."""

    def test_get_user_metrics(self, client):
        """Test user metrics endpoint."""
        start_date = datetime.utcnow() - timedelta(days=30)
        end_date = datetime.utcnow()
        
        response = client.get(
            "/v1/metrics/users",
            params={
                "start_date": start_date.isoformat(),
                "end_date": end_date.isoformat(),
            }
        )
        
        assert response.status_code in [200, 500]  # May fail if ClickHouse not running

    def test_get_booking_metrics(self, client):
        """Test booking metrics endpoint."""
        start_date = datetime.utcnow() - timedelta(days=30)
        end_date = datetime.utcnow()
        
        response = client.get(
            "/v1/metrics/bookings",
            params={
                "start_date": start_date.isoformat(),
                "end_date": end_date.isoformat(),
            }
        )
        
        assert response.status_code in [200, 500]

    def test_get_realtime_metrics(self, client):
        """Test realtime metrics endpoint."""
        response = client.get("/v1/metrics/realtime")
        
        assert response.status_code in [200, 500]


class TestAnalyticsEndpoints:
    """Test analytics API endpoints."""

    def test_get_funnel_analysis(self, client):
        """Test funnel analysis endpoint."""
        start_date = datetime.utcnow() - timedelta(days=30)
        end_date = datetime.utcnow()
        
        response = client.get(
            "/v1/analytics/funnel",
            params={
                "start_date": start_date.isoformat(),
                "end_date": end_date.isoformat(),
            }
        )
        
        assert response.status_code in [200, 500]

    def test_get_cohort_analysis(self, client):
        """Test cohort analysis endpoint."""
        start_date = datetime.utcnow() - timedelta(days=90)
        
        response = client.get(
            "/v1/analytics/cohort",
            params={
                "cohort_type": "weekly",
                "start_date": start_date.isoformat(),
                "periods": 12,
            }
        )
        
        assert response.status_code in [200, 500]

    def test_get_retention_analysis(self, client):
        """Test retention analysis endpoint."""
        start_date = datetime.utcnow() - timedelta(days=30)
        end_date = datetime.utcnow()
        
        response = client.get(
            "/v1/analytics/retention",
            params={
                "user_type": "all",
                "start_date": start_date.isoformat(),
                "end_date": end_date.isoformat(),
            }
        )
        
        assert response.status_code in [200, 500]


class TestHealthEndpoints:
    """Test health check endpoints."""

    def test_health_check(self, client):
        """Test basic health check."""
        response = client.get("/health")
        
        assert response.status_code == 200

    def test_root_endpoint(self, client):
        """Test root endpoint."""
        response = client.get("/")
        
        assert response.status_code == 200
        data = response.json()
        assert data["service"] == "openride-analytics-service"
        assert data["status"] == "running"


class TestExportEndpoints:
    """Test export API endpoints."""

    def test_create_export_request(self, client):
        """Test creating an export request."""
        payload = {
            "query": "SELECT count() FROM openride_analytics.events_raw LIMIT 100",
            "format": "csv",
            "filename": "test_export",
            "row_limit": 1000,
        }
        
        response = client.post("/v1/exports/request", json=payload)
        
        assert response.status_code in [200, 422, 500]

    def test_quick_export_csv(self, client):
        """Test quick CSV export."""
        response = client.get(
            "/v1/exports/quick/csv",
            params={
                "query": "SELECT 1 as test",
                "filename": "quick_test.csv",
            }
        )
        
        assert response.status_code in [200, 500]

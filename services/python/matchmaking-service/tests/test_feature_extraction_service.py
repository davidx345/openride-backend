"""Tests for ML feature extraction service."""

import pytest
import numpy as np
from datetime import datetime, time
from decimal import Decimal
from uuid import uuid4

from app.services.feature_extraction_service import FeatureExtractionService
from app.schemas.matching import MatchRequest
from app.models.route import Route


@pytest.fixture
def feature_extractor():
    """Create feature extraction service."""
    return FeatureExtractionService()


@pytest.fixture
def sample_route():
    """Create sample route for testing."""
    route = Route(
        id=uuid4(),
        driver_id=uuid4(),
        name="Test Route",
        departure_time=time(8, 30),
        seats_available=3,
        seats_total=4,
        base_price=Decimal("1500.00"),
        origin_hub_id=uuid4(),
        destination_hub_id=uuid4(),
    )
    return route


@pytest.fixture
def sample_request():
    """Create sample match request."""
    return MatchRequest(
        origin_lat=6.5244,
        origin_lon=3.3792,
        dest_lat=6.4281,
        dest_lon=3.4219,
        desired_time=time(8, 0),
        min_seats=1,
    )


def test_extract_features_basic(feature_extractor, sample_route, sample_request):
    """Test basic feature extraction."""
    all_prices = [Decimal("1200.00"), Decimal("1500.00"), Decimal("1800.00")]
    
    features = feature_extractor.extract_features(
        route=sample_route,
        request=sample_request,
        all_prices=all_prices,
        driver_stats=None,
        match_type="EXACT",
        origin_distance_km=0.5,
        dest_distance_km=0.3,
    )
    
    # Should have 24 features
    assert len(features) == 24
    assert isinstance(features, np.ndarray)
    
    # Check temporal features
    assert features[0] == 8  # hour_of_day
    assert 0 <= features[1] <= 6  # day_of_week
    assert features[2] in [0.0, 1.0]  # is_weekend
    assert features[3] == 1.0  # is_rush_hour (8am is rush hour)


def test_extract_features_with_driver_stats(feature_extractor, sample_route, sample_request):
    """Test feature extraction with driver stats."""
    driver_stats = {
        "rating_avg": 4.7,
        "rating_count": 150,
        "cancellation_rate": 0.02,
        "completed_trips": 500,
    }
    
    features = feature_extractor.extract_features(
        route=sample_route,
        request=sample_request,
        all_prices=[Decimal("1500.00")],
        driver_stats=driver_stats,
        match_type="EXACT",
    )
    
    # Check driver features (indices 14-17)
    assert features[14] == 4.7  # driver_rating
    assert features[15] == 150  # driver_rating_count
    assert features[16] == 0.02  # driver_cancellation_rate
    assert features[17] == 500  # driver_completed_trips


def test_extract_features_match_type(feature_extractor, sample_route, sample_request):
    """Test match type feature extraction."""
    # Test EXACT match
    features_exact = feature_extractor.extract_features(
        route=sample_route,
        request=sample_request,
        all_prices=[Decimal("1500.00")],
        match_type="EXACT",
    )
    assert features_exact[4] == 1.0  # match_type_exact
    
    # Test PARTIAL match
    features_partial = feature_extractor.extract_features(
        route=sample_route,
        request=sample_request,
        all_prices=[Decimal("1500.00")],
        match_type="PARTIAL",
    )
    assert features_partial[4] == 0.0  # match_type_exact


def test_extract_features_time_diff(feature_extractor, sample_route, sample_request):
    """Test time difference feature extraction."""
    # Route departs at 8:30, request is for 8:00
    features = feature_extractor.extract_features(
        route=sample_route,
        request=sample_request,
        all_prices=[Decimal("1500.00")],
    )
    
    # Time diff should be 30 minutes
    assert features[5] == 30.0  # time_diff_minutes
    assert features[6] == 0.5  # time_diff_normalized (30/60)


def test_extract_features_price_features(feature_extractor, sample_route, sample_request):
    """Test price-related features."""
    all_prices = [Decimal("1000.00"), Decimal("1500.00"), Decimal("2000.00")]
    
    features = feature_extractor.extract_features(
        route=sample_route,
        request=sample_request,
        all_prices=all_prices,
    )
    
    # Check price features (indices 7-9)
    assert features[7] == 1500.0  # price_per_seat
    assert features[8] == 2.0  # price_rank (middle price)
    assert 0.4 <= features[9] <= 0.6  # price_percentile (middle range)


def test_extract_features_availability(feature_extractor, sample_route, sample_request):
    """Test availability features."""
    features = feature_extractor.extract_features(
        route=sample_route,
        request=sample_request,
        all_prices=[Decimal("1500.00")],
    )
    
    # Check availability features (indices 18-19)
    assert features[18] == 3.0  # seats_available
    assert features[19] == 0.75  # seats_utilization (3/4)


def test_extract_features_distance(feature_extractor, sample_route, sample_request):
    """Test distance features."""
    features = feature_extractor.extract_features(
        route=sample_route,
        request=sample_request,
        all_prices=[Decimal("1500.00")],
        origin_distance_km=0.5,
        dest_distance_km=0.3,
    )
    
    # Check distance features (indices 20-22)
    assert features[20] == 0.5  # origin_distance_km
    assert features[21] == 0.3  # dest_distance_km
    assert features[22] == 0.8  # total_distance_km


def test_extract_features_hub_features(feature_extractor, sample_route, sample_request):
    """Test hub features."""
    features = feature_extractor.extract_features(
        route=sample_route,
        request=sample_request,
        all_prices=[Decimal("1500.00")],
    )
    
    # Check hub features (indices 23-24)
    assert features[23] == 1.0  # has_origin_hub
    assert features[24] == 1.0  # has_dest_hub (route has both hubs)


def test_extract_batch_features(feature_extractor, sample_request):
    """Test batch feature extraction."""
    routes = [
        Route(
            id=uuid4(),
            driver_id=uuid4(),
            name=f"Route {i}",
            departure_time=time(8, i * 15),
            seats_available=i + 1,
            seats_total=4,
            base_price=Decimal(f"{1000 + i * 200}.00"),
            origin_hub_id=uuid4() if i % 2 == 0 else None,
            destination_hub_id=uuid4() if i % 2 == 0 else None,
        )
        for i in range(3)
    ]
    
    driver_stats_map = {}
    match_types = {str(r.id): "EXACT" if i % 2 == 0 else "PARTIAL" for i, r in enumerate(routes)}
    distances = {r.id: {"origin_km": 0.5, "dest_km": 0.3} for r in routes}
    
    features = feature_extractor.extract_batch_features(
        routes=routes,
        request=sample_request,
        driver_stats_map=driver_stats_map,
        match_types=match_types,
        distances=distances,
    )
    
    # Should return matrix with shape (3, 24)
    assert features.shape == (3, 24)
    assert isinstance(features, np.ndarray)


def test_get_feature_names(feature_extractor):
    """Test feature names retrieval."""
    names = feature_extractor.get_feature_names()
    
    assert len(names) == 24
    assert "hour_of_day" in names
    assert "driver_rating" in names
    assert "has_origin_hub" in names


def test_get_feature_importance_weights(feature_extractor):
    """Test feature importance weights."""
    weights = feature_extractor.get_feature_importance_weights()
    
    assert len(weights) == 24
    assert "match_type_exact" in weights
    assert weights["match_type_exact"] > 0  # Positive weight
    assert weights["time_diff_normalized"] < 0  # Negative weight (lower diff = better)
    assert weights["driver_cancellation_rate"] < 0  # Negative weight


def test_calculate_ml_score(feature_extractor, sample_route, sample_request):
    """Test ML score calculation."""
    features = feature_extractor.extract_features(
        route=sample_route,
        request=sample_request,
        all_prices=[Decimal("1500.00")],
        match_type="EXACT",
    )
    
    score = feature_extractor.calculate_ml_score(features)
    
    # Score should be between 0 and 1
    assert 0.0 <= score <= 1.0
    assert isinstance(score, float)


def test_explain_ml_score(feature_extractor, sample_route, sample_request):
    """Test ML score explanation."""
    features = feature_extractor.extract_features(
        route=sample_route,
        request=sample_request,
        all_prices=[Decimal("1500.00")],
        match_type="EXACT",
    )
    
    explanation = feature_extractor.explain_ml_score(features)
    
    assert "total_score" in explanation
    assert "contributions" in explanation
    assert "feature_count" in explanation
    assert explanation["feature_count"] == 24
    
    # Check that contributions are sorted
    contributions = list(explanation["contributions"].values())
    assert len(contributions) <= 10  # Top 10 only

"""Tests for enhanced ML scoring service."""

import pytest
import numpy as np
from decimal import Decimal

from app.services.scoring_service import RouteScorer


@pytest.fixture
def scorer():
    """Create route scorer."""
    return RouteScorer()


def test_calculate_ml_score(scorer):
    """Test ML-based scoring."""
    # Create sample features (24 features)
    features = np.array([
        # Temporal
        8, 1, 0, 1,  # morning, Monday, not weekend, rush hour
        # Match quality
        1.0, 10, 0.16,  # exact match, 10 min diff, normalized
        # Pricing
        1500, 2, 0.5,  # price, rank, percentile
        # Route
        10, 5, 0.5,  # length, dest position, normalized
        # Driver
        4.5, 100, 0.02, 200,  # rating, count, cancellation, trips
        # Availability
        3, 0.75,  # seats, utilization
        # Distance
        0.5, 0.3, 0.8,  # origin, dest, total
        # Hub
        1, 1,  # has origin, has dest
    ], dtype=np.float32)
    
    feature_weights = {
        "hour_of_day": 0.02,
        "day_of_week": 0.01,
        "is_weekend": 0.02,
        "is_rush_hour": 0.03,
        "match_type_exact": 0.15,
        "time_diff_minutes": -0.008,
        "time_diff_normalized": -0.10,
        "price_per_seat": -0.001,
        "price_rank": -0.05,
        "price_percentile": -0.08,
        "route_length": 0.01,
        "destination_position": 0.0,
        "destination_position_normalized": 0.02,
        "driver_rating": 0.12,
        "driver_rating_count": 0.03,
        "driver_cancellation_rate": -0.15,
        "driver_completed_trips": 0.05,
        "seats_available": 0.04,
        "seats_utilization": 0.03,
        "origin_distance_km": -0.08,
        "dest_distance_km": -0.06,
        "total_distance_km": -0.05,
        "has_origin_hub": 0.06,
        "has_dest_hub": 0.04,
    }
    
    score = scorer.calculate_ml_score(features, feature_weights)
    
    # Should return valid score
    assert 0.0 <= score <= 1.0
    assert isinstance(score, float)


def test_calculate_hybrid_score(scorer):
    """Test hybrid scoring (rule-based + ML)."""
    rule_based_score = 0.8
    ml_score = 0.6
    
    # Default alpha = 0.6 (60% rule-based, 40% ML)
    hybrid = scorer.calculate_hybrid_score(rule_based_score, ml_score)
    
    expected = 0.6 * 0.8 + 0.4 * 0.6  # 0.48 + 0.24 = 0.72
    assert abs(hybrid - expected) < 0.01
    assert 0.0 <= hybrid <= 1.0


def test_calculate_hybrid_score_custom_alpha(scorer):
    """Test hybrid scoring with custom alpha."""
    rule_based_score = 0.7
    ml_score = 0.5
    alpha = 0.8  # 80% rule-based, 20% ML
    
    hybrid = scorer.calculate_hybrid_score(rule_based_score, ml_score, alpha)
    
    expected = 0.8 * 0.7 + 0.2 * 0.5  # 0.56 + 0.10 = 0.66
    assert abs(hybrid - expected) < 0.01


def test_calculate_hybrid_score_invalid_alpha(scorer):
    """Test hybrid scoring with invalid alpha (should use default)."""
    rule_based_score = 0.8
    ml_score = 0.6
    
    # Invalid alpha > 1
    hybrid = scorer.calculate_hybrid_score(rule_based_score, ml_score, alpha=1.5)
    
    # Should use default alpha = 0.6
    expected = 0.6 * 0.8 + 0.4 * 0.6
    assert abs(hybrid - expected) < 0.01


def test_explain_hybrid_score(scorer):
    """Test hybrid score explanation."""
    rule_based_score = 0.75
    ml_score = 0.65
    alpha = 0.6
    hybrid_score = 0.6 * 0.75 + 0.4 * 0.65  # 0.71
    route_explanation = "✓ Good match"
    
    explanation = scorer.explain_hybrid_score(
        rule_based_score=rule_based_score,
        ml_score=ml_score,
        hybrid_score=hybrid_score,
        alpha=alpha,
        route_explanation=route_explanation,
    )
    
    assert explanation["final_score"] == hybrid_score
    assert explanation["scoring_mode"] == "hybrid"
    
    # Check components
    assert "rule_based" in explanation["components"]
    assert "ml_based" in explanation["components"]
    
    # Check rule-based component
    rb_component = explanation["components"]["rule_based"]
    assert rb_component["score"] == rule_based_score
    assert rb_component["weight"] == alpha
    assert abs(rb_component["contribution"] - 0.45) < 0.01
    assert rb_component["explanation"] == route_explanation
    
    # Check ML component
    ml_component = explanation["components"]["ml_based"]
    assert ml_component["score"] == ml_score
    assert ml_component["weight"] == 1 - alpha
    assert abs(ml_component["contribution"] - 0.26) < 0.01


def test_hybrid_score_edge_cases(scorer):
    """Test hybrid scoring edge cases."""
    # Both scores are 0
    hybrid = scorer.calculate_hybrid_score(0.0, 0.0, alpha=0.5)
    assert hybrid == 0.0
    
    # Both scores are 1
    hybrid = scorer.calculate_hybrid_score(1.0, 1.0, alpha=0.5)
    assert hybrid == 1.0
    
    # Alpha = 1 (100% rule-based)
    hybrid = scorer.calculate_hybrid_score(0.8, 0.3, alpha=1.0)
    assert abs(hybrid - 0.8) < 0.01
    
    # Alpha = 0 (100% ML)
    hybrid = scorer.calculate_hybrid_score(0.8, 0.3, alpha=0.0)
    assert abs(hybrid - 0.3) < 0.01


def test_ml_score_with_negative_weights(scorer):
    """Test ML score calculation with negative weights (penalties)."""
    # Features with high penalty values
    features = np.array([
        # Temporal
        8, 1, 0, 1,
        # Match quality (high time diff = penalty)
        0, 60, 1.0,  # not exact, 60 min diff (BAD)
        # Pricing (high price = penalty)
        3000, 5, 0.9,  # expensive (BAD)
        # Route
        10, 5, 0.5,
        # Driver (high cancellation = penalty)
        3.0, 10, 0.3, 50,  # low rating, high cancellation (BAD)
        # Availability
        1, 0.25,  # low availability
        # Distance (far = penalty)
        5.0, 4.0, 9.0,  # far distances (BAD)
        # Hub
        0, 0,  # no hubs
    ], dtype=np.float32)
    
    feature_weights = {
        "hour_of_day": 0.02,
        "day_of_week": 0.01,
        "is_weekend": 0.02,
        "is_rush_hour": 0.03,
        "match_type_exact": 0.15,
        "time_diff_minutes": -0.008,  # PENALTY
        "time_diff_normalized": -0.10,  # PENALTY
        "price_per_seat": -0.001,  # PENALTY
        "price_rank": -0.05,  # PENALTY
        "price_percentile": -0.08,  # PENALTY
        "route_length": 0.01,
        "destination_position": 0.0,
        "destination_position_normalized": 0.02,
        "driver_rating": 0.12,
        "driver_rating_count": 0.03,
        "driver_cancellation_rate": -0.15,  # PENALTY
        "driver_completed_trips": 0.05,
        "seats_available": 0.04,
        "seats_utilization": 0.03,
        "origin_distance_km": -0.08,  # PENALTY
        "dest_distance_km": -0.06,  # PENALTY
        "total_distance_km": -0.05,  # PENALTY
        "has_origin_hub": 0.06,
        "has_dest_hub": 0.04,
    }
    
    score = scorer.calculate_ml_score(features, feature_weights)
    
    # Score should be valid (may be low due to penalties)
    assert 0.0 <= score <= 1.0
    
    # Should be lower than a good route
    good_features = np.array([
        8, 1, 0, 1,
        1.0, 5, 0.08,  # exact, low time diff
        1200, 1, 0.1,  # cheap
        10, 5, 0.5,
        4.8, 200, 0.01, 500,  # great driver
        3, 0.75,
        0.3, 0.2, 0.5,  # close
        1, 1,  # hubs
    ], dtype=np.float32)
    
    good_score = scorer.calculate_ml_score(good_features, feature_weights)
    
    # Good route should score higher
    assert good_score > score

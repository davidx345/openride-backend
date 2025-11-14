"""Analytics schemas for funnel, cohort, and retention analysis."""

from datetime import datetime
from decimal import Decimal
from typing import Dict, List, Optional

from pydantic import BaseModel, Field


class FunnelStep(BaseModel):
    """Funnel step data."""
    
    step_name: str
    step_number: int
    count: int
    conversion_rate: float = Field(..., description="Conversion rate from step 1 (%)")
    drop_off_rate: float = Field(..., description="Drop-off rate from previous step (%)")


class FunnelAnalysisResponse(BaseModel):
    """Funnel analysis response."""
    
    date_range: str
    funnel_steps: List[FunnelStep]
    total_users_entered: int
    total_users_converted: int
    overall_conversion_rate: float


class CohortData(BaseModel):
    """Cohort retention data."""
    
    cohort_period: str = Field(..., description="Cohort period (e.g., 2024-01, 2024-W05)")
    cohort_size: int = Field(..., description="Number of users in cohort")
    retention_by_period: Dict[str, float] = Field(
        ...,
        description="Retention rates by period (e.g., period_0: 100%, period_1: 45%)"
    )


class CohortAnalysisResponse(BaseModel):
    """Cohort analysis response."""
    
    cohort_type: str = Field(..., description="daily, weekly, or monthly")
    start_date: datetime
    periods_analyzed: int
    cohorts: List[CohortData]


class RetentionCohort(BaseModel):
    """Retention cohort data."""
    
    cohort_date: str
    users_count: int
    d1_retained: int
    d7_retained: int
    d30_retained: int
    d1_rate: float
    d7_rate: float
    d30_rate: float


class RetentionAnalysisResponse(BaseModel):
    """Retention analysis response."""
    
    user_type: str
    date_range: str
    total_users: int
    d1_retention_rate: float
    d7_retention_rate: float
    d30_retention_rate: float
    d1_retained_users: int
    d7_retained_users: int
    d30_retained_users: int


class GeographicDataPoint(BaseModel):
    """Geographic distribution data point."""
    
    state: str
    city: str
    value: float


class GeographicDistributionResponse(BaseModel):
    """Geographic distribution response."""
    
    metric_type: str = Field(..., description="bookings, revenue, or users")
    date_range: str
    data_points: List[GeographicDataPoint]
    total_value: float


class TimeSeriesDataPoint(BaseModel):
    """Time series data point."""
    
    timestamp: datetime
    value: float

"""Pydantic schemas for metrics and analytics responses."""

from datetime import date, datetime
from decimal import Decimal
from typing import Any, Dict, List, Optional
from uuid import UUID

from pydantic import BaseModel, Field


class TimeSeriesDataPoint(BaseModel):
    """Single data point in a time series."""

    timestamp: datetime
    value: float
    metadata: Optional[Dict[str, Any]] = None


class TimeSeriesResponse(BaseModel):
    """Time series data response."""

    metric_name: str
    data_points: List[TimeSeriesDataPoint]
    start_date: datetime
    end_date: datetime
    granularity: str = Field(..., description="hourly, daily, weekly, monthly")
    total_count: int


class UserMetricsResponse(BaseModel):
    """User metrics response."""

    date_range: str
    dau: int = Field(..., description="Daily Active Users")
    wau: int = Field(..., description="Weekly Active Users")
    mau: int = Field(..., description="Monthly Active Users")
    new_registrations: int
    total_users: int
    kyc_completion_rate: float
    driver_activation_rate: float
    time_series: Optional[List[TimeSeriesDataPoint]] = None


class BookingMetricsResponse(BaseModel):
    """Booking metrics response."""

    date_range: str
    bookings_created: int
    bookings_confirmed: int
    bookings_cancelled: int
    bookings_completed: int
    conversion_rate: float = Field(..., description="Created to Confirmed ratio")
    cancellation_rate: float
    avg_booking_value: Decimal
    total_booking_value: Decimal
    time_series: Optional[List[TimeSeriesDataPoint]] = None


class PaymentMetricsResponse(BaseModel):
    """Payment metrics response."""

    date_range: str
    payments_initiated: int
    payments_success: int
    payments_failed: int
    payments_refunded: int
    success_rate: float
    avg_processing_time_ms: float
    total_revenue: Decimal
    time_series: Optional[List[TimeSeriesDataPoint]] = None


class TripMetricsResponse(BaseModel):
    """Trip metrics response."""

    date_range: str
    trips_started: int
    trips_completed: int
    trips_cancelled: int
    completion_rate: float
    avg_duration_minutes: float
    avg_distance_km: float
    on_time_performance: float
    total_driver_earnings: Decimal
    total_platform_commission: Decimal
    active_drivers: int
    time_series: Optional[List[TimeSeriesDataPoint]] = None


class DriverEarningsResponse(BaseModel):
    """Driver earnings response."""

    driver_id: UUID
    driver_name: Optional[str] = None
    total_trips: int
    total_earnings: Decimal
    avg_earnings_per_trip: Decimal
    total_distance_km: float
    active_days: int
    rating_avg: Optional[float] = None


class PopularRouteResponse(BaseModel):
    """Popular route response."""

    route_id: UUID
    route_name: str
    origin_city: str
    destination_city: str
    driver_id: UUID
    driver_name: Optional[str] = None
    total_bookings: int
    total_revenue: Decimal
    avg_occupancy: float
    seats_total: int


class GeographicMetricsResponse(BaseModel):
    """Geographic distribution metrics."""

    state: str
    city: Optional[str] = None
    total_bookings: int
    total_revenue: Decimal
    total_users: int
    active_routes: int


class FunnelStep(BaseModel):
    """Single step in a conversion funnel."""

    step_name: str
    step_order: int
    total_count: int
    conversion_rate: Optional[float] = None
    drop_off_rate: Optional[float] = None


class FunnelAnalysisResponse(BaseModel):
    """Funnel analysis response."""

    funnel_name: str
    date_range: str
    steps: List[FunnelStep]
    overall_conversion_rate: float


class CohortData(BaseModel):
    """Cohort analysis data."""

    cohort_date: date
    cohort_size: int
    retention_rates: Dict[int, float]  # {period: retention_rate}


class CohortAnalysisResponse(BaseModel):
    """Cohort analysis response."""

    analysis_type: str  # 'user_retention', 'driver_retention'
    cohorts: List[CohortData]
    avg_retention_rate: float


class RealtimeMetricsResponse(BaseModel):
    """Real-time dashboard metrics."""

    timestamp: datetime
    active_users: int
    active_drivers: int
    active_trips: int
    bookings_last_hour: int
    revenue_last_hour: Decimal
    avg_wait_time_minutes: Optional[float] = None
    system_health: str = Field(..., pattern="^(healthy|degraded|critical)$")


class ExportRequest(BaseModel):
    """Export request schema."""

    metric_type: str = Field(..., description="user_metrics, booking_metrics, etc.")
    start_date: datetime
    end_date: datetime
    format: str = Field(default="csv", pattern="^(csv|excel|json)$")
    filters: Optional[Dict[str, Any]] = None
    include_time_series: bool = False


class ReportScheduleRequest(BaseModel):
    """Schedule report request schema."""

    report_name: str
    report_type: str
    schedule_cron: str = Field(..., description="Cron expression")
    output_format: str = Field(default="csv", pattern="^(csv|excel|json)$")
    recipients: List[str] = Field(..., description="Email addresses")
    parameters: Optional[Dict[str, Any]] = None
    is_active: bool = True


class ReportScheduleResponse(BaseModel):
    """Report schedule response."""

    id: UUID
    report_name: str
    report_type: str
    schedule_cron: str
    output_format: str
    recipients: List[str]
    is_active: bool
    last_run_at: Optional[datetime] = None
    next_run_at: Optional[datetime] = None
    created_at: datetime

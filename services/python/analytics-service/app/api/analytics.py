"""Advanced analytics API endpoints."""

from datetime import datetime, timedelta
from typing import List, Optional

from fastapi import APIRouter, Depends, Query
from clickhouse_connect.driver import Client

from app.core import get_clickhouse
from app.core.logging import get_logger
from app.schemas.analytics import (
    CohortAnalysisResponse,
    FunnelAnalysisResponse,
    FunnelStep,
    GeographicDistributionResponse,
    RetentionAnalysisResponse,
)
from app.services.analytics import AnalyticsService

logger = get_logger(__name__)

router = APIRouter()


def get_analytics_service(ch: Client = Depends(get_clickhouse)) -> AnalyticsService:
    """Dependency to get analytics service."""
    return AnalyticsService(ch)


@router.get("/analytics/funnel", response_model=FunnelAnalysisResponse)
async def get_funnel_analysis(
    start_date: datetime = Query(..., description="Start date (ISO 8601)"),
    end_date: datetime = Query(..., description="End date (ISO 8601)"),
    service: AnalyticsService = Depends(get_analytics_service)
):
    """
    Get conversion funnel analysis.
    
    Tracks user journey: Registration → Route Search → Booking → Payment → Trip Completion
    """
    return await service.get_funnel_analysis(start_date, end_date)


@router.get("/analytics/cohort", response_model=CohortAnalysisResponse)
async def get_cohort_analysis(
    cohort_type: str = Query("weekly", regex="^(daily|weekly|monthly)$"),
    start_date: datetime = Query(..., description="Start date (ISO 8601)"),
    periods: int = Query(12, ge=1, le=52, description="Number of periods to analyze"),
    service: AnalyticsService = Depends(get_analytics_service)
):
    """
    Get cohort analysis showing user retention over time.
    
    Groups users by registration date and tracks their activity in subsequent periods.
    """
    return await service.get_cohort_analysis(cohort_type, start_date, periods)


@router.get("/analytics/retention", response_model=RetentionAnalysisResponse)
async def get_retention_analysis(
    user_type: str = Query("all", regex="^(all|rider|driver)$"),
    start_date: datetime = Query(..., description="Start date (ISO 8601)"),
    end_date: datetime = Query(..., description="End date (ISO 8601)"),
    service: AnalyticsService = Depends(get_analytics_service)
):
    """
    Get user retention metrics.
    
    Calculates D1, D7, D30 retention rates for users registered in the period.
    """
    return await service.get_retention_analysis(user_type, start_date, end_date)


@router.get("/analytics/geographic", response_model=GeographicDistributionResponse)
async def get_geographic_distribution(
    start_date: datetime = Query(..., description="Start date (ISO 8601)"),
    end_date: datetime = Query(..., description="End date (ISO 8601)"),
    metric: str = Query("bookings", regex="^(bookings|revenue|users)$"),
    service: AnalyticsService = Depends(get_analytics_service)
):
    """
    Get geographic distribution of activity.
    
    Shows distribution of bookings, revenue, or users by state and city.
    """
    return await service.get_geographic_distribution(start_date, end_date, metric)


@router.get("/analytics/trends")
async def get_time_series_trends(
    metric: str = Query(..., description="Metric to analyze"),
    start_date: datetime = Query(..., description="Start date (ISO 8601)"),
    end_date: datetime = Query(..., description="End date (ISO 8601)"),
    granularity: str = Query("daily", regex="^(hourly|daily|weekly|monthly)$"),
    service: AnalyticsService = Depends(get_analytics_service)
):
    """
    Get time-series trends for any metric.
    
    Supports: bookings, revenue, users, trips, etc.
    """
    return await service.get_time_series_trends(metric, start_date, end_date, granularity)

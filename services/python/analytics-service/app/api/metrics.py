"""Metrics API endpoints."""

from datetime import datetime, timedelta

from fastapi import APIRouter, Depends, Query
from clickhouse_connect.driver import Client

from app.core import get_clickhouse
from app.core.logging import get_logger
from app.schemas.metrics import (
    BookingMetricsResponse,
    PaymentMetricsResponse,
    RealtimeMetricsResponse,
    TripMetricsResponse,
    UserMetricsResponse,
)
from app.services.aggregations import MetricsAggregationService

logger = get_logger(__name__)

router = APIRouter()


def get_aggregation_service(ch: Client = Depends(get_clickhouse)) -> MetricsAggregationService:
    """Dependency to get metrics aggregation service."""
    return MetricsAggregationService(ch)


@router.get("/metrics/users", response_model=UserMetricsResponse)
async def get_user_metrics(
    start_date: datetime = Query(..., description="Start date (ISO 8601)"),
    end_date: datetime = Query(..., description="End date (ISO 8601)"),
    service: MetricsAggregationService = Depends(get_aggregation_service)
):
    """Get user metrics (DAU/WAU/MAU, registrations, KYC rate)."""
    return await service.get_user_metrics(start_date, end_date)


@router.get("/metrics/bookings", response_model=BookingMetricsResponse)
async def get_booking_metrics(
    start_date: datetime = Query(..., description="Start date (ISO 8601)"),
    end_date: datetime = Query(..., description="End date (ISO 8601)"),
    service: MetricsAggregationService = Depends(get_aggregation_service)
):
    """Get booking metrics (conversion rate, cancellations, revenue)."""
    return await service.get_booking_metrics(start_date, end_date)


@router.get("/metrics/payments", response_model=PaymentMetricsResponse)
async def get_payment_metrics(
    start_date: datetime = Query(..., description="Start date (ISO 8601)"),
    end_date: datetime = Query(..., description="End date (ISO 8601)"),
    service: MetricsAggregationService = Depends(get_aggregation_service)
):
    """Get payment metrics (success rate, revenue, processing time)."""
    return await service.get_payment_metrics(start_date, end_date)


@router.get("/metrics/trips", response_model=TripMetricsResponse)
async def get_trip_metrics(
    start_date: datetime = Query(..., description="Start date (ISO 8601)"),
    end_date: datetime = Query(..., description="End date (ISO 8601)"),
    service: MetricsAggregationService = Depends(get_aggregation_service)
):
    """Get trip metrics (completion rate, duration, distance, earnings)."""
    return await service.get_trip_metrics(start_date, end_date)


@router.get("/metrics/realtime", response_model=RealtimeMetricsResponse)
async def get_realtime_metrics(
    service: MetricsAggregationService = Depends(get_aggregation_service)
):
    """Get real-time dashboard metrics (last hour)."""
    return await service.get_realtime_metrics()


@router.get("/metrics/drivers")
async def get_driver_metrics(
    start_date: datetime = Query(..., description="Start date (ISO 8601)"),
    end_date: datetime = Query(..., description="End date (ISO 8601)"),
    limit: int = Query(100, ge=1, le=1000, description="Number of drivers to return"),
    ch: Client = Depends(get_clickhouse)
):
    """Get driver earnings and performance metrics."""
    # TODO: Implement driver metrics
    return {"message": "Driver metrics endpoint - to be implemented"}


@router.get("/metrics/routes")
async def get_route_metrics(
    start_date: datetime = Query(..., description="Start date (ISO 8601)"),
    end_date: datetime = Query(..., description="End date (ISO 8601)"),
    limit: int = Query(50, ge=1, le=500, description="Number of routes to return"),
    ch: Client = Depends(get_clickhouse)
):
    """Get popular routes and performance metrics."""
    # TODO: Implement route metrics
    return {"message": "Route metrics endpoint - to be implemented"}

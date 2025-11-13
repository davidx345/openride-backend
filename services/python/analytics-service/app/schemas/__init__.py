"""Schemas package initialization."""

from app.schemas.booking_events import (
    BookingCancelledEvent,
    BookingCompletedEvent,
    BookingConfirmedEvent,
    BookingCreatedEvent,
)
from app.schemas.location_events import DriverLocationUpdatedEvent
from app.schemas.metrics import (
    BookingMetricsResponse,
    CohortAnalysisResponse,
    CohortData,
    DriverEarningsResponse,
    ExportRequest,
    FunnelAnalysisResponse,
    FunnelStep,
    GeographicMetricsResponse,
    PaymentMetricsResponse,
    PopularRouteResponse,
    RealtimeMetricsResponse,
    ReportScheduleRequest,
    ReportScheduleResponse,
    TimeSeriesDataPoint,
    TimeSeriesResponse,
    TripMetricsResponse,
    UserMetricsResponse,
)
from app.schemas.payment_events import (
    PaymentFailedEvent,
    PaymentInitiatedEvent,
    PaymentRefundedEvent,
    PaymentSuccessEvent,
)
from app.schemas.route_events import (
    RouteActivatedEvent,
    RouteCancelledEvent,
    RouteCreatedEvent,
    RouteUpdatedEvent,
)
from app.schemas.trip_events import (
    TripCancelledEvent,
    TripCompletedEvent,
    TripStartedEvent,
)
from app.schemas.user_events import (
    UserActivityEvent,
    UserKYCVerifiedEvent,
    UserRegisteredEvent,
    UserUpgradedToDriverEvent,
)

__all__ = [
    # User Events
    "UserRegisteredEvent",
    "UserKYCVerifiedEvent",
    "UserUpgradedToDriverEvent",
    "UserActivityEvent",
    # Booking Events
    "BookingCreatedEvent",
    "BookingConfirmedEvent",
    "BookingCancelledEvent",
    "BookingCompletedEvent",
    # Payment Events
    "PaymentInitiatedEvent",
    "PaymentSuccessEvent",
    "PaymentFailedEvent",
    "PaymentRefundedEvent",
    # Trip Events
    "TripStartedEvent",
    "TripCompletedEvent",
    "TripCancelledEvent",
    # Route Events
    "RouteCreatedEvent",
    "RouteActivatedEvent",
    "RouteCancelledEvent",
    "RouteUpdatedEvent",
    # Location Events
    "DriverLocationUpdatedEvent",
    # Metrics
    "TimeSeriesDataPoint",
    "TimeSeriesResponse",
    "UserMetricsResponse",
    "BookingMetricsResponse",
    "PaymentMetricsResponse",
    "TripMetricsResponse",
    "DriverEarningsResponse",
    "PopularRouteResponse",
    "GeographicMetricsResponse",
    "FunnelStep",
    "FunnelAnalysisResponse",
    "CohortData",
    "CohortAnalysisResponse",
    "RealtimeMetricsResponse",
    "ExportRequest",
    "ReportScheduleRequest",
    "ReportScheduleResponse",
]

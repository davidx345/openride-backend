"""Schemas package initialization"""
from app.schemas.location import (
    DriverLocationResponse,
    DriverLocationUpdate,
    DriverStatusUpdate,
    LocationHistoryQuery,
    LocationHistoryResponse,
    LocationPoint,
    NearbyDriverResponse,
    NearbyDriversQuery,
)
from app.schemas.trip import (
    TripCreate,
    TripListQuery,
    TripMetrics,
    TripResponse,
    TripStatusUpdate,
)
from app.schemas.websocket import (
    ConnectionEvent,
    DriverLocationEvent,
    DriverStatusEvent,
    ErrorEvent,
    RiderSubscribeEvent,
    RiderUnsubscribeEvent,
    TripUpdateEvent,
    WebSocketMessage,
)

__all__ = [
    # Location schemas
    'DriverLocationResponse',
    'DriverLocationUpdate',
    'DriverStatusUpdate',
    'LocationHistoryQuery',
    'LocationHistoryResponse',
    'LocationPoint',
    'NearbyDriverResponse',
    'NearbyDriversQuery',
    # Trip schemas
    'TripCreate',
    'TripListQuery',
    'TripMetrics',
    'TripResponse',
    'TripStatusUpdate',
    # WebSocket schemas
    'ConnectionEvent',
    'DriverLocationEvent',
    'DriverStatusEvent',
    'ErrorEvent',
    'RiderSubscribeEvent',
    'RiderUnsubscribeEvent',
    'TripUpdateEvent',
    'WebSocketMessage',
]

"""Matching request and response schemas."""

from datetime import time
from decimal import Decimal
from typing import Any
from uuid import UUID

from pydantic import BaseModel, Field, field_validator


class MatchRequest(BaseModel):
    """Request schema for route matching."""

    rider_id: UUID = Field(..., description="ID of the rider requesting match")
    origin_lat: float = Field(..., ge=-90, le=90, description="Origin latitude")
    origin_lon: float = Field(..., ge=-180, le=180, description="Origin longitude")
    dest_lat: float | None = Field(None, ge=-90, le=90, description="Destination latitude")
    dest_lon: float | None = Field(None, ge=-180, le=180, description="Destination longitude")
    desired_time: time = Field(..., description="Desired departure time")
    max_price: Decimal | None = Field(None, ge=0, description="Maximum acceptable price")
    min_seats: int = Field(1, ge=1, le=10, description="Minimum required seats")
    radius_km: float = Field(5.0, ge=0.1, le=20.0, description="Search radius in km")

    @field_validator("dest_lat", "dest_lon")
    @classmethod
    def validate_destination(cls, v: float | None, info: Any) -> float | None:
        """Validate that if one destination coordinate is provided, both must be."""
        values = info.data
        if "dest_lat" in values and "dest_lon" in values:
            dest_lat = values.get("dest_lat")
            dest_lon = values.get("dest_lon")
            if (dest_lat is None) != (dest_lon is None):
                raise ValueError("Both dest_lat and dest_lon must be provided together")
        return v


class ScoreBreakdown(BaseModel):
    """Score breakdown for match explanation."""

    route_match: float = Field(..., ge=0, le=1, description="Route match score (0-1)")
    time_match: float = Field(..., ge=0, le=1, description="Time match score (0-1)")
    rating: float = Field(..., ge=0, le=1, description="Driver rating score (0-1)")
    price: float = Field(..., ge=0, le=1, description="Price score (0-1)")


class MatchResult(BaseModel):
    """Individual match result."""

    route_id: UUID = Field(..., description="Matched route ID")
    driver_id: UUID = Field(..., description="Driver ID")
    final_score: float = Field(..., ge=0, le=1, description="Final composite score")
    scores: ScoreBreakdown = Field(..., description="Score breakdown")
    explanation: str = Field(..., description="Human-readable match explanation")
    recommended: bool = Field(..., description="Whether this match is recommended")

    # Route details
    route_name: str | None = None
    departure_time: time | None = None
    seats_available: int | None = None
    base_price: Decimal | None = None
    driver_rating: float | None = None


class MatchResponse(BaseModel):
    """Response schema for matching endpoint."""

    matches: list[MatchResult] = Field(..., description="List of matched routes")
    total_candidates: int = Field(..., description="Total routes considered")
    matched_candidates: int = Field(..., description="Routes that matched criteria")
    execution_time_ms: int = Field(..., description="Execution time in milliseconds")


class RouteSearchParams(BaseModel):
    """Parameters for route search."""

    lat: float = Field(..., ge=-90, le=90, description="Origin latitude")
    lng: float = Field(..., ge=-180, le=180, description="Origin longitude")
    dest_lat: float | None = Field(None, ge=-90, le=90, description="Destination latitude")
    dest_lng: float | None = Field(None, ge=-180, le=180, description="Destination longitude")
    time: str | None = Field(None, description="Desired departure time (HH:MM:SS)")
    radius: float = Field(5.0, ge=0.1, le=20.0, description="Search radius in km")
    limit: int = Field(20, ge=1, le=100, description="Maximum results")
    offset: int = Field(0, ge=0, description="Pagination offset")


class StopInfo(BaseModel):
    """Stop information in route."""

    id: UUID
    name: str
    lat: Decimal
    lon: Decimal
    address: str | None = None
    stop_order: int
    price_from_origin: Decimal


class DriverInfo(BaseModel):
    """Driver information."""

    id: UUID
    name: str | None = None
    rating: float | None = None


class RouteSearchResult(BaseModel):
    """Single route search result."""

    id: UUID
    name: str
    driver: DriverInfo
    departure_time: time
    seats_available: int
    base_price: Decimal
    stops: list[StopInfo]
    match_score: float | None = None
    explanation: str | None = None


class RouteSearchResponse(BaseModel):
    """Response schema for route search."""

    results: list[RouteSearchResult]
    total: int
    page: int
    limit: int
    has_more: bool

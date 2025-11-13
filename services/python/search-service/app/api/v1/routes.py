"""Search and discovery API endpoints."""

import logging
from datetime import time
from decimal import Decimal
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from geoalchemy2.functions import ST_Distance, ST_SetSRID, ST_MakePoint
from sqlalchemy import and_, cast, func, or_, select
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import selectinload
from sqlalchemy.types import Geography

from app.core.cache import get_cache, CacheManager
from app.core.config import get_settings
from app.core.database import get_db

settings = get_settings()
router = APIRouter(prefix="/routes", tags=["search"])
logger = logging.getLogger(__name__)


# Import models directly (simplified for search service)
from sqlalchemy.orm import declarative_base, Mapped, mapped_column, relationship
from sqlalchemy import String, Time, ARRAY, Numeric, ForeignKey, CheckConstraint
from geoalchemy2 import Geometry
from enum import Enum

Base = declarative_base()


class RouteStatus(str, Enum):
    """Route status."""
    ACTIVE = "ACTIVE"
    PAUSED = "PAUSED"
    CANCELLED = "CANCELLED"


class Route(Base):
    """Simplified Route model for search."""
    __tablename__ = "routes"
    
    id: Mapped[UUID] = mapped_column(primary_key=True)
    driver_id: Mapped[UUID] = mapped_column(nullable=False)
    vehicle_id: Mapped[UUID] = mapped_column(nullable=False)
    name: Mapped[str] = mapped_column(String(200))
    departure_time: Mapped[time] = mapped_column(Time)
    active_days: Mapped[list[int]] = mapped_column(ARRAY(item_type=int))
    seats_total: Mapped[int]
    seats_available: Mapped[int]
    base_price: Mapped[Decimal] = mapped_column(Numeric(10, 2))
    status: Mapped[str] = mapped_column(String(20))
    
    # Geospatial fields
    start_lat: Mapped[Decimal | None] = mapped_column(Numeric(10, 8))
    start_lon: Mapped[Decimal | None] = mapped_column(Numeric(11, 8))
    end_lat: Mapped[Decimal | None] = mapped_column(Numeric(10, 8))
    end_lon: Mapped[Decimal | None] = mapped_column(Numeric(11, 8))
    start_location: Mapped[bytes | None] = mapped_column(Geometry("POINT", 4326))
    end_location: Mapped[bytes | None] = mapped_column(Geometry("POINT", 4326))
    
    route_stops: Mapped[list["RouteStop"]] = relationship(
        "RouteStop", back_populates="route", order_by="RouteStop.stop_order"
    )


class Stop(Base):
    """Stop model."""
    __tablename__ = "stops"
    
    id: Mapped[UUID] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(200))
    lat: Mapped[Decimal] = mapped_column(Numeric(10, 8))
    lon: Mapped[Decimal] = mapped_column(Numeric(11, 8))
    location: Mapped[bytes] = mapped_column(Geometry("POINT", 4326))
    address: Mapped[str | None] = mapped_column(String(500))
    landmark: Mapped[str | None] = mapped_column(String(200))


class RouteStop(Base):
    """Route-Stop association."""
    __tablename__ = "route_stops"
    
    route_id: Mapped[UUID] = mapped_column(ForeignKey("routes.id", ondelete="CASCADE"), primary_key=True)
    stop_id: Mapped[UUID] = mapped_column(ForeignKey("stops.id"), primary_key=True)
    stop_order: Mapped[int]
    planned_arrival_offset_minutes: Mapped[int]
    price_from_origin: Mapped[Decimal] = mapped_column(Numeric(10, 2))
    
    route: Mapped[Route] = relationship("Route", back_populates="route_stops")
    stop: Mapped[Stop] = relationship("Stop")


@router.get(
    "",
    status_code=status.HTTP_200_OK,
    summary="Search routes by location and time",
    description="Public endpoint to search for available routes near origin/destination",
)
async def search_routes(
    lat: Annotated[float, Query(ge=-90, le=90, description="Origin latitude")],
    lng: Annotated[float, Query(ge=-180, le=180, description="Origin longitude")],
    dest_lat: Annotated[float | None, Query(None, ge=-90, le=90, description="Destination latitude")] = None,
    dest_lng: Annotated[float | None, Query(None, ge=-180, le=180, description="Destination longitude")] = None,
    time_str: Annotated[str | None, Query(None, alias="time", description="Desired time (HH:MM:SS)")] = None,
    radius: Annotated[float, Query(5.0, ge=0.1, le=20.0, description="Search radius (km)")] = 5.0,
    limit: Annotated[int, Query(20, ge=1, le=100, description="Max results")] = 20,
    offset: Annotated[int, Query(0, ge=0, description="Pagination offset")] = 0,
    db: AsyncSession = Depends(get_db),
    cache: CacheManager = Depends(get_cache),
) -> dict:
    """
    Search for routes near specified location.
    
    Returns routes with stops within radius of origin and optionally destination.
    """
    try:
        # Check cache
        cache_key = cache._get_cache_key(
            "search",
            lat=round(lat, 4),
            lng=round(lng, 4),
            dlat=round(dest_lat, 4) if dest_lat else None,
            dlng=round(dest_lng, 4) if dest_lng else None,
            time=time_str,
            radius=radius,
            limit=limit,
            offset=offset,
        )
        
        cached_result = await cache.get(cache_key)
        if cached_result:
            logger.info(f"Cache hit for search query")
            return cached_result
        
        # Build query
        radius_meters = radius * 1000
        origin_point = cast(ST_SetSRID(ST_MakePoint(lng, lat), 4326), Geography)
        
        # Find routes with stops near origin
        stmt = (
            select(Route)
            .distinct()
            .join(RouteStop, Route.id == RouteStop.route_id)
            .join(Stop, RouteStop.stop_id == Stop.id)
            .where(
                and_(
                    Route.status == RouteStatus.ACTIVE.value,
                    Route.seats_available > 0,
                    or_(
                        func.ST_DWithin(
                            cast(Stop.location, Geography),
                            origin_point,
                            radius_meters
                        )
                    )
                )
            )
            .options(selectinload(Route.route_stops).selectinload(RouteStop.stop))
            .limit(limit)
            .offset(offset)
        )
        
        result = await db.execute(stmt)
        routes = result.scalars().unique().all()
        
        # Format response
        route_results = []
        for route in routes:
            stops_data = [
                {
                    "id": str(rs.stop.id),
                    "name": rs.stop.name,
                    "lat": float(rs.stop.lat),
                    "lon": float(rs.stop.lon),
                    "address": rs.stop.address,
                    "stop_order": rs.stop_order,
                    "price_from_origin": float(rs.price_from_origin),
                }
                for rs in sorted(route.route_stops, key=lambda x: x.stop_order)
            ]
            
            route_results.append({
                "id": str(route.id),
                "name": route.name,
                "driver_id": str(route.driver_id),
                "departure_time": route.departure_time.isoformat(),
                "seats_available": route.seats_available,
                "base_price": float(route.base_price),
                "stops": stops_data,
            })
        
        response = {
            "results": route_results,
            "total": len(route_results),
            "limit": limit,
            "offset": offset,
            "has_more": len(route_results) == limit,
        }
        
        # Cache result
        await cache.set(cache_key, response, ttl=settings.redis_cache_ttl)
        
        logger.info(f"Search completed: {len(route_results)} routes found")
        return response
        
    except Exception as e:
        logger.error(f"Search error: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Search failed"
        )


@router.get(
    "/{route_id}",
    status_code=status.HTTP_200_OK,
    summary="Get route details",
    description="Get detailed information about a specific route",
)
async def get_route(
    route_id: UUID,
    db: AsyncSession = Depends(get_db),
) -> dict:
    """Get detailed route information."""
    try:
        stmt = (
            select(Route)
            .where(Route.id == route_id)
            .options(selectinload(Route.route_stops).selectinload(RouteStop.stop))
        )
        
        result = await db.execute(stmt)
        route = result.scalar_one_or_none()
        
        if not route:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail=f"Route {route_id} not found"
            )
        
        stops_data = [
            {
                "id": str(rs.stop.id),
                "name": rs.stop.name,
                "lat": float(rs.stop.lat),
                "lon": float(rs.stop.lon),
                "address": rs.stop.address,
                "landmark": rs.stop.landmark,
                "stop_order": rs.stop_order,
                "planned_arrival_offset_minutes": rs.planned_arrival_offset_minutes,
                "price_from_origin": float(rs.price_from_origin),
            }
            for rs in sorted(route.route_stops, key=lambda x: x.stop_order)
        ]
        
        return {
            "id": str(route.id),
            "driver_id": str(route.driver_id),
            "vehicle_id": str(route.vehicle_id),
            "name": route.name,
            "departure_time": route.departure_time.isoformat(),
            "active_days": route.active_days,
            "seats_total": route.seats_total,
            "seats_available": route.seats_available,
            "base_price": float(route.base_price),
            "status": route.status,
            "stops": stops_data,
        }
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Get route error: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to fetch route"
        )

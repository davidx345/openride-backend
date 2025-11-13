"""
Route service for business logic.
"""
import logging
from typing import List, Optional
from uuid import UUID
from datetime import datetime, time, date

from sqlalchemy.ext.asyncio import AsyncSession
from redis.asyncio import Redis

from app.core.exceptions import (
    RouteNotFoundException,
    UnauthorizedException,
    ValidationException,
    VehicleNotFoundException,
    RateLimitExceededException,
    KYCNotVerifiedException
)
from app.core.config import settings
from app.models.route import Route, RouteStatus
from app.repositories.route_repository import RouteRepository
from app.repositories.vehicle_repository import VehicleRepository
from app.schemas.route import RouteCreate, RouteUpdate, RouteStopInput
from app.schemas.stop import StopCreate
from app.services.stop_service import StopService

logger = logging.getLogger(__name__)


class RouteService:
    """Service for route business logic."""
    
    def __init__(self, db: AsyncSession):
        """
        Initialize service with database session.
        
        Args:
            db: Async database session
        """
        self.db = db
        self.repo = RouteRepository(db)
        self.vehicle_repo = VehicleRepository(db)
        self.stop_service = StopService(db)
    
    async def create_route(
        self,
        driver_id: UUID,
        route_data: RouteCreate,
        skip_kyc_check: bool = False
    ) -> Route:
        """
        Create a new route with stops.
        
        Args:
            driver_id: Driver's user ID
            route_data: Route creation data
            skip_kyc_check: Skip KYC verification (for testing)
            
        Returns:
            Created route with stops
            
        Raises:
            VehicleNotFoundException: If vehicle not found or doesn't belong to driver
            ValidationException: If validation fails
            RateLimitExceededException: If rate limit exceeded
        """
        # Check rate limit: max routes per day
        routes_today = await self.repo.count_routes_created_today(driver_id)
        if routes_today >= settings.RATE_LIMIT_ROUTES_PER_DAY:
            raise RateLimitExceededException(
                f"Maximum {settings.RATE_LIMIT_ROUTES_PER_DAY} routes per day exceeded"
            )
        
        # Verify vehicle exists and belongs to driver
        vehicle = await self.vehicle_repo.get_by_id(route_data.vehicle_id)
        if not vehicle or vehicle.driver_id != driver_id:
            raise VehicleNotFoundException("Vehicle not found or doesn't belong to driver")
        
        if not vehicle.is_active:
            raise ValidationException("Vehicle is not active")
        
        # Validate seats
        if route_data.seats_total > vehicle.seats_total:
            raise ValidationException(
                f"Route seats ({route_data.seats_total}) cannot exceed vehicle capacity ({vehicle.seats_total})"
            )
        
        try:
            # Create route
            logger.info(f"Creating route for driver {driver_id}: {route_data.name}")
            route = await self.repo.create(driver_id, route_data)
            
            # Process stops
            stop_ids = []
            stop_orders = []
            arrival_offsets = []
            prices = []
            
            for idx, stop_input in enumerate(route_data.stops):
                if stop_input.stop_id:
                    # Use existing stop
                    stop = await self.stop_service.get_stop(stop_input.stop_id)
                else:
                    # Create or get stop
                    stop_create = StopCreate(
                        name=stop_input.name,
                        lat=stop_input.lat,
                        lon=stop_input.lon,
                        address=stop_input.address,
                        landmark=stop_input.landmark
                    )
                    stop, _ = await self.stop_service.create_or_get_stop(stop_create)
                
                stop_ids.append(stop.id)
                stop_orders.append(idx)
                arrival_offsets.append(stop_input.planned_arrival_offset_minutes)
                prices.append(float(stop_input.price_from_origin))
            
            # Add stops to route
            await self.repo.add_stops(
                route.id,
                stop_ids,
                stop_orders,
                arrival_offsets,
                prices
            )
            
            # Commit transaction
            await self.repo.commit()
            
            # Reload route with stops
            route = await self.repo.get_by_id(route.id, include_stops=True)
            
            logger.info(f"Created route {route.id} with {len(stop_ids)} stops")
            
            return route
            
        except Exception as e:
            await self.repo.rollback()
            logger.error(f"Failed to create route: {e}")
            raise
    
    async def get_route(
        self,
        route_id: UUID,
        driver_id: Optional[UUID] = None,
        include_stops: bool = True
    ) -> Route:
        """
        Get route by ID.
        
        Args:
            route_id: Route ID
            driver_id: Optional driver ID for authorization check
            include_stops: Whether to include route stops
            
        Returns:
            Route
            
        Raises:
            RouteNotFoundException: If route not found
            UnauthorizedException: If route doesn't belong to driver
        """
        route = await self.repo.get_by_id(route_id, include_stops)
        
        if not route:
            raise RouteNotFoundException(f"Route {route_id} not found")
        
        if driver_id and route.driver_id != driver_id:
            raise UnauthorizedException("Route does not belong to this driver")
        
        return route
    
    async def get_driver_routes(
        self,
        driver_id: UUID,
        status: Optional[str] = None,
        include_stops: bool = False
    ) -> List[Route]:
        """
        Get all routes for a driver.
        
        Args:
            driver_id: Driver's user ID
            status: Filter by status (ACTIVE, PAUSED, CANCELLED)
            include_stops: Whether to include route stops
            
        Returns:
            List of routes
        """
        route_status = RouteStatus(status) if status else None
        return await self.repo.get_by_driver(driver_id, route_status, include_stops)
    
    async def update_route(
        self,
        route_id: UUID,
        driver_id: UUID,
        route_data: RouteUpdate
    ) -> Route:
        """
        Update route.
        
        Args:
            route_id: Route ID
            driver_id: Driver's user ID
            route_data: Update data
            
        Returns:
            Updated route
            
        Raises:
            RouteNotFoundException: If route not found
            UnauthorizedException: If route doesn't belong to driver
            ValidationException: If validation fails
        """
        route = await self.get_route(route_id, driver_id, include_stops=False)
        
        # Validate status transitions
        if route_data.status:
            new_status = RouteStatus(route_data.status)
            if route.status == RouteStatus.CANCELLED and new_status != RouteStatus.CANCELLED:
                raise ValidationException("Cannot update a cancelled route")
        
        try:
            logger.info(f"Updating route {route_id}")
            updated_route = await self.repo.update(route, route_data)
            await self.repo.commit()
            logger.info(f"Updated route {route_id}")
            
            return updated_route
            
        except Exception as e:
            await self.repo.rollback()
            logger.error(f"Failed to update route: {e}")
            raise
    
    async def delete_route(
        self,
        route_id: UUID,
        driver_id: UUID
    ) -> None:
        """
        Delete route (soft delete by setting status to CANCELLED).
        
        Args:
            route_id: Route ID
            driver_id: Driver's user ID
            
        Raises:
            RouteNotFoundException: If route not found
            UnauthorizedException: If route doesn't belong to driver
        """
        route = await self.get_route(route_id, driver_id, include_stops=False)
        
        try:
            logger.info(f"Cancelling route {route_id}")
            route.status = RouteStatus.CANCELLED
            await self.repo.commit()
            logger.info(f"Cancelled route {route_id}")
            
        except Exception as e:
            await self.repo.rollback()
            logger.error(f"Failed to cancel route: {e}")
            raise
    
    async def get_active_routes(
        self,
        include_stops: bool = True,
        limit: Optional[int] = None
    ) -> List[Route]:
        """
        Get all active routes (for search/discovery).
        
        Args:
            include_stops: Whether to include route stops
            limit: Maximum number of results
            
        Returns:
            List of active routes
        """
        return await self.repo.get_active_routes(include_stops, limit)

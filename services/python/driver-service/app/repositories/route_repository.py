"""
Route repository for database operations.
"""
from typing import List, Optional
from uuid import UUID
from datetime import date

from sqlalchemy import select, delete, func
from sqlalchemy.orm import selectinload
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.route import Route, RouteStop, RouteStatus
from app.schemas.route import RouteCreate, RouteUpdate


class RouteRepository:
    """Repository for route database operations."""
    
    def __init__(self, db: AsyncSession):
        """
        Initialize repository with database session.
        
        Args:
            db: Async database session
        """
        self.db = db
    
    async def create(self, driver_id: UUID, route_data: RouteCreate) -> Route:
        """
        Create a new route (without stops).
        
        Args:
            driver_id: Driver's user ID
            route_data: Route creation data
            
        Returns:
            Created route
        """
        route = Route(
            driver_id=driver_id,
            vehicle_id=route_data.vehicle_id,
            name=route_data.name,
            departure_time=route_data.departure_time,
            active_days=route_data.active_days,
            schedule_rrule=route_data.schedule_rrule,
            seats_total=route_data.seats_total,
            seats_available=route_data.seats_total,  # Initialize to total
            base_price=route_data.base_price,
            notes=route_data.notes,
            status=RouteStatus.ACTIVE
        )
        self.db.add(route)
        await self.db.flush()  # Get route ID without committing
        return route
    
    async def add_stops(
        self,
        route_id: UUID,
        stop_ids: List[UUID],
        stop_orders: List[int],
        arrival_offsets: List[int],
        prices: List[float]
    ) -> List[RouteStop]:
        """
        Add stops to a route.
        
        Args:
            route_id: Route ID
            stop_ids: List of stop IDs
            stop_orders: List of stop orders
            arrival_offsets: List of arrival offset minutes
            prices: List of prices from origin
            
        Returns:
            List of created route stops
        """
        route_stops = []
        for i in range(len(stop_ids)):
            route_stop = RouteStop(
                route_id=route_id,
                stop_id=stop_ids[i],
                stop_order=stop_orders[i],
                planned_arrival_offset_minutes=arrival_offsets[i],
                price_from_origin=prices[i]
            )
            self.db.add(route_stop)
            route_stops.append(route_stop)
        
        await self.db.flush()
        return route_stops
    
    async def get_by_id(
        self,
        route_id: UUID,
        include_stops: bool = True
    ) -> Optional[Route]:
        """
        Get route by ID.
        
        Args:
            route_id: Route ID
            include_stops: Whether to include route stops
            
        Returns:
            Route if found, None otherwise
        """
        query = select(Route).where(Route.id == route_id)
        
        if include_stops:
            query = query.options(
                selectinload(Route.route_stops).selectinload(RouteStop.stop)
            )
        
        result = await self.db.execute(query)
        return result.scalar_one_or_none()
    
    async def get_by_driver(
        self,
        driver_id: UUID,
        status: Optional[RouteStatus] = None,
        include_stops: bool = False
    ) -> List[Route]:
        """
        Get all routes for a driver.
        
        Args:
            driver_id: Driver's user ID
            status: Filter by status
            include_stops: Whether to include route stops
            
        Returns:
            List of routes
        """
        query = select(Route).where(Route.driver_id == driver_id)
        
        if status:
            query = query.where(Route.status == status)
        
        if include_stops:
            query = query.options(
                selectinload(Route.route_stops).selectinload(RouteStop.stop)
            )
        
        query = query.order_by(Route.created_at.desc())
        
        result = await self.db.execute(query)
        return list(result.scalars().all())
    
    async def get_active_routes(
        self,
        include_stops: bool = True,
        limit: Optional[int] = None
    ) -> List[Route]:
        """
        Get all active routes.
        
        Args:
            include_stops: Whether to include route stops
            limit: Maximum number of results
            
        Returns:
            List of active routes
        """
        query = select(Route).where(Route.status == RouteStatus.ACTIVE)
        
        if include_stops:
            query = query.options(
                selectinload(Route.route_stops).selectinload(RouteStop.stop)
            )
        
        if limit:
            query = query.limit(limit)
        
        result = await self.db.execute(query)
        return list(result.scalars().all())
    
    async def update(self, route: Route, route_data: RouteUpdate) -> Route:
        """
        Update route.
        
        Args:
            route: Route to update
            route_data: Update data
            
        Returns:
            Updated route
        """
        update_data = route_data.model_dump(exclude_unset=True)
        
        # Handle status enum conversion
        if 'status' in update_data:
            update_data['status'] = RouteStatus(update_data['status'])
        
        for field, value in update_data.items():
            setattr(route, field, value)
        
        await self.db.flush()
        return route
    
    async def delete_stops(self, route_id: UUID) -> None:
        """
        Delete all stops for a route.
        
        Args:
            route_id: Route ID
        """
        await self.db.execute(
            delete(RouteStop).where(RouteStop.route_id == route_id)
        )
        await self.db.flush()
    
    async def delete(self, route: Route) -> None:
        """
        Delete route (cascade deletes stops).
        
        Args:
            route: Route to delete
        """
        await self.db.delete(route)
        await self.db.flush()
    
    async def commit(self) -> None:
        """Commit transaction."""
        await self.db.commit()
    
    async def rollback(self) -> None:
        """Rollback transaction."""
        await self.db.rollback()
    
    async def count_routes_created_today(self, driver_id: UUID) -> int:
        """
        Count routes created by driver today.
        
        Args:
            driver_id: Driver's user ID
            
        Returns:
            Number of routes created today
        """
        today = date.today()
        result = await self.db.execute(
            select(Route).where(
                Route.driver_id == driver_id,
                func.date(Route.created_at) == today
            )
        )
        return len(list(result.scalars().all()))

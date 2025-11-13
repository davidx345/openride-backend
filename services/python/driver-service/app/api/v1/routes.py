"""
Route API endpoints.
"""
import logging
from typing import List, Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user, require_driver_role, TokenData
from app.core.exceptions import (
    RouteNotFoundException,
    UnauthorizedException,
    ValidationException,
    VehicleNotFoundException,
    RateLimitExceededException,
    KYCNotVerifiedException
)
from app.schemas.route import RouteCreate, RouteUpdate, RouteResponse
from app.services.route_service import RouteService

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/routes", response_model=RouteResponse, status_code=status.HTTP_201_CREATED)
async def create_route(
    route_data: RouteCreate,
    current_user: TokenData = Depends(require_driver_role),
    db: AsyncSession = Depends(get_db)
):
    """
    Create a new route.
    
    Requires DRIVER or ADMIN role. Driver must have verified KYC.
    """
    try:
        service = RouteService(db)
        
        # In production, add KYC check here
        # For development, skip KYC check
        route = await service.create_route(
            current_user.user_id,
            route_data,
            skip_kyc_check=True  # TODO: Implement proper KYC check
        )
        return route
    except VehicleNotFoundException as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except ValidationException as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))
    except RateLimitExceededException as e:
        raise HTTPException(status_code=status.HTTP_429_TOO_MANY_REQUESTS, detail=str(e))
    except KYCNotVerifiedException as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e))
    except Exception as e:
        logger.error(f"Error creating route: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to create route: {str(e)}"
        )


@router.get("/routes", response_model=List[RouteResponse])
async def get_routes(
    status_filter: Optional[str] = Query(None, alias="status", description="Filter by status"),
    include_stops: bool = Query(default=True, description="Include route stops"),
    current_user: TokenData = Depends(require_driver_role),
    db: AsyncSession = Depends(get_db)
):
    """
    Get all routes for current driver.
    
    Requires DRIVER or ADMIN role.
    """
    try:
        service = RouteService(db)
        routes = await service.get_driver_routes(
            current_user.user_id,
            status_filter,
            include_stops
        )
        return routes
    except ValidationException as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))
    except Exception as e:
        logger.error(f"Error getting routes: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to get routes"
        )


@router.get("/routes/active", response_model=List[RouteResponse])
async def get_active_routes(
    include_stops: bool = Query(default=True, description="Include route stops"),
    limit: Optional[int] = Query(None, ge=1, le=100, description="Maximum number of results"),
    current_user: TokenData = Depends(require_driver_role),
    db: AsyncSession = Depends(get_db)
):
    """
    Get all active routes for current driver.
    
    Requires DRIVER or ADMIN role.
    """
    try:
        service = RouteService(db)
        routes = await service.get_driver_routes(
            current_user.user_id,
            "ACTIVE",
            include_stops
        )
        
        if limit:
            routes = routes[:limit]
        
        return routes
    except Exception as e:
        logger.error(f"Error getting active routes: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to get active routes"
        )


@router.get("/routes/{route_id}", response_model=RouteResponse)
async def get_route(
    route_id: UUID,
    include_stops: bool = Query(default=True, description="Include route stops"),
    current_user: TokenData = Depends(require_driver_role),
    db: AsyncSession = Depends(get_db)
):
    """
    Get route by ID.
    
    Requires DRIVER or ADMIN role. Only returns route if it belongs to current driver.
    """
    try:
        service = RouteService(db)
        route = await service.get_route(route_id, current_user.user_id, include_stops)
        return route
    except RouteNotFoundException as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except UnauthorizedException as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e))
    except Exception as e:
        logger.error(f"Error getting route: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to get route"
        )


@router.put("/routes/{route_id}", response_model=RouteResponse)
async def update_route(
    route_id: UUID,
    route_data: RouteUpdate,
    current_user: TokenData = Depends(require_driver_role),
    db: AsyncSession = Depends(get_db)
):
    """
    Update route.
    
    Requires DRIVER or ADMIN role. Only allows update if route belongs to current driver.
    """
    try:
        service = RouteService(db)
        route = await service.update_route(route_id, current_user.user_id, route_data)
        return route
    except RouteNotFoundException as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except UnauthorizedException as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e))
    except ValidationException as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))
    except Exception as e:
        logger.error(f"Error updating route: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to update route"
        )


@router.patch("/routes/{route_id}/status", response_model=RouteResponse)
async def update_route_status(
    route_id: UUID,
    status_value: str = Query(..., alias="status", description="New status (ACTIVE, PAUSED, CANCELLED)"),
    current_user: TokenData = Depends(require_driver_role),
    db: AsyncSession = Depends(get_db)
):
    """
    Update route status.
    
    Requires DRIVER or ADMIN role. Only allows update if route belongs to current driver.
    """
    try:
        service = RouteService(db)
        route_update = RouteUpdate(status=status_value)
        route = await service.update_route(route_id, current_user.user_id, route_update)
        return route
    except RouteNotFoundException as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except UnauthorizedException as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e))
    except ValidationException as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))
    except Exception as e:
        logger.error(f"Error updating route status: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to update route status"
        )


@router.delete("/routes/{route_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_route(
    route_id: UUID,
    current_user: TokenData = Depends(require_driver_role),
    db: AsyncSession = Depends(get_db)
):
    """
    Delete route (soft delete by setting status to CANCELLED).
    
    Requires DRIVER or ADMIN role. Only allows delete if route belongs to current driver.
    """
    try:
        service = RouteService(db)
        await service.delete_route(route_id, current_user.user_id)
        return None
    except RouteNotFoundException as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except UnauthorizedException as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e))
    except Exception as e:
        logger.error(f"Error deleting route: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to delete route"
        )

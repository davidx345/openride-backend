"""
Vehicle API endpoints.
"""
import logging
from typing import List
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.security import get_current_user, require_driver_role, TokenData
from app.core.exceptions import (
    VehicleNotFoundException,
    UnauthorizedException,
    ValidationException
)
from app.schemas.vehicle import VehicleCreate, VehicleUpdate, VehicleResponse
from app.services.vehicle_service import VehicleService

router = APIRouter()
logger = logging.getLogger(__name__)


@router.post("/vehicles", response_model=VehicleResponse, status_code=status.HTTP_201_CREATED)
async def create_vehicle(
    vehicle_data: VehicleCreate,
    current_user: TokenData = Depends(require_driver_role),
    db: AsyncSession = Depends(get_db)
):
    """
    Create a new vehicle.
    
    Requires DRIVER or ADMIN role.
    """
    try:
        service = VehicleService(db)
        vehicle = await service.create_vehicle(current_user.user_id, vehicle_data)
        return vehicle
    except ValidationException as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))
    except Exception as e:
        logger.error(f"Error creating vehicle: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to create vehicle"
        )


@router.get("/vehicles", response_model=List[VehicleResponse])
async def get_vehicles(
    include_inactive: bool = False,
    current_user: TokenData = Depends(require_driver_role),
    db: AsyncSession = Depends(get_db)
):
    """
    Get all vehicles for current driver.
    
    Requires DRIVER or ADMIN role.
    """
    try:
        service = VehicleService(db)
        vehicles = await service.get_driver_vehicles(current_user.user_id, include_inactive)
        return vehicles
    except Exception as e:
        logger.error(f"Error getting vehicles: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to get vehicles"
        )


@router.get("/vehicles/{vehicle_id}", response_model=VehicleResponse)
async def get_vehicle(
    vehicle_id: UUID,
    current_user: TokenData = Depends(require_driver_role),
    db: AsyncSession = Depends(get_db)
):
    """
    Get vehicle by ID.
    
    Requires DRIVER or ADMIN role. Only returns vehicle if it belongs to current driver.
    """
    try:
        service = VehicleService(db)
        vehicle = await service.get_vehicle(vehicle_id, current_user.user_id)
        return vehicle
    except VehicleNotFoundException as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except UnauthorizedException as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e))
    except Exception as e:
        logger.error(f"Error getting vehicle: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to get vehicle"
        )


@router.put("/vehicles/{vehicle_id}", response_model=VehicleResponse)
async def update_vehicle(
    vehicle_id: UUID,
    vehicle_data: VehicleUpdate,
    current_user: TokenData = Depends(require_driver_role),
    db: AsyncSession = Depends(get_db)
):
    """
    Update vehicle.
    
    Requires DRIVER or ADMIN role. Only allows update if vehicle belongs to current driver.
    """
    try:
        service = VehicleService(db)
        vehicle = await service.update_vehicle(vehicle_id, current_user.user_id, vehicle_data)
        return vehicle
    except VehicleNotFoundException as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except UnauthorizedException as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e))
    except ValidationException as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=str(e))
    except Exception as e:
        logger.error(f"Error updating vehicle: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to update vehicle"
        )


@router.delete("/vehicles/{vehicle_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_vehicle(
    vehicle_id: UUID,
    current_user: TokenData = Depends(require_driver_role),
    db: AsyncSession = Depends(get_db)
):
    """
    Delete vehicle (soft delete).
    
    Requires DRIVER or ADMIN role. Only allows delete if vehicle belongs to current driver.
    """
    try:
        service = VehicleService(db)
        await service.delete_vehicle(vehicle_id, current_user.user_id)
        return None
    except VehicleNotFoundException as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except UnauthorizedException as e:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail=str(e))
    except Exception as e:
        logger.error(f"Error deleting vehicle: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to delete vehicle"
        )

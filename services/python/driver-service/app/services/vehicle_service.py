"""
Vehicle service for business logic.
"""
import logging
from typing import List, Optional
from uuid import UUID

from sqlalchemy.ext.asyncio import AsyncSession

from app.core.exceptions import (
    VehicleNotFoundException,
    UnauthorizedException,
    ValidationException
)
from app.models.vehicle import Vehicle
from app.repositories.vehicle_repository import VehicleRepository
from app.schemas.vehicle import VehicleCreate, VehicleUpdate

logger = logging.getLogger(__name__)


class VehicleService:
    """Service for vehicle business logic."""
    
    def __init__(self, db: AsyncSession):
        """
        Initialize service with database session.
        
        Args:
            db: Async database session
        """
        self.db = db
        self.repo = VehicleRepository(db)
    
    async def create_vehicle(
        self,
        driver_id: UUID,
        vehicle_data: VehicleCreate
    ) -> Vehicle:
        """
        Create a new vehicle.
        
        Args:
            driver_id: Driver's user ID
            vehicle_data: Vehicle creation data
            
        Returns:
            Created vehicle
            
        Raises:
            ValidationException: If plate number already exists
        """
        # Check if plate number already exists
        existing = await self.repo.get_by_plate_number(vehicle_data.plate_number)
        if existing:
            raise ValidationException(
                f"Vehicle with plate number {vehicle_data.plate_number} already exists"
            )
        
        logger.info(f"Creating vehicle for driver {driver_id}: {vehicle_data.plate_number}")
        vehicle = await self.repo.create(driver_id, vehicle_data)
        logger.info(f"Created vehicle {vehicle.id}")
        
        return vehicle
    
    async def get_vehicle(
        self,
        vehicle_id: UUID,
        driver_id: Optional[UUID] = None
    ) -> Vehicle:
        """
        Get vehicle by ID.
        
        Args:
            vehicle_id: Vehicle ID
            driver_id: Optional driver ID for authorization check
            
        Returns:
            Vehicle
            
        Raises:
            VehicleNotFoundException: If vehicle not found
            UnauthorizedException: If vehicle doesn't belong to driver
        """
        vehicle = await self.repo.get_by_id(vehicle_id)
        
        if not vehicle:
            raise VehicleNotFoundException(f"Vehicle {vehicle_id} not found")
        
        if driver_id and vehicle.driver_id != driver_id:
            raise UnauthorizedException("Vehicle does not belong to this driver")
        
        return vehicle
    
    async def get_driver_vehicles(
        self,
        driver_id: UUID,
        include_inactive: bool = False
    ) -> List[Vehicle]:
        """
        Get all vehicles for a driver.
        
        Args:
            driver_id: Driver's user ID
            include_inactive: Whether to include inactive vehicles
            
        Returns:
            List of vehicles
        """
        return await self.repo.get_by_driver(driver_id, include_inactive)
    
    async def update_vehicle(
        self,
        vehicle_id: UUID,
        driver_id: UUID,
        vehicle_data: VehicleUpdate
    ) -> Vehicle:
        """
        Update vehicle.
        
        Args:
            vehicle_id: Vehicle ID
            driver_id: Driver's user ID
            vehicle_data: Update data
            
        Returns:
            Updated vehicle
            
        Raises:
            VehicleNotFoundException: If vehicle not found
            UnauthorizedException: If vehicle doesn't belong to driver
            ValidationException: If plate number already exists
        """
        vehicle = await self.get_vehicle(vehicle_id, driver_id)
        
        # Check plate number uniqueness if updating
        if vehicle_data.plate_number:
            existing = await self.repo.get_by_plate_number(vehicle_data.plate_number)
            if existing and existing.id != vehicle_id:
                raise ValidationException(
                    f"Vehicle with plate number {vehicle_data.plate_number} already exists"
                )
        
        logger.info(f"Updating vehicle {vehicle_id}")
        updated_vehicle = await self.repo.update(vehicle, vehicle_data)
        logger.info(f"Updated vehicle {vehicle_id}")
        
        return updated_vehicle
    
    async def delete_vehicle(
        self,
        vehicle_id: UUID,
        driver_id: UUID
    ) -> None:
        """
        Soft delete vehicle.
        
        Args:
            vehicle_id: Vehicle ID
            driver_id: Driver's user ID
            
        Raises:
            VehicleNotFoundException: If vehicle not found
            UnauthorizedException: If vehicle doesn't belong to driver
        """
        vehicle = await self.get_vehicle(vehicle_id, driver_id)
        
        logger.info(f"Deleting vehicle {vehicle_id}")
        await self.repo.delete(vehicle)
        logger.info(f"Deleted vehicle {vehicle_id}")

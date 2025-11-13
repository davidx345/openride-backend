"""
Vehicle repository for database operations.
"""
from typing import List, Optional
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.vehicle import Vehicle
from app.schemas.vehicle import VehicleCreate, VehicleUpdate


class VehicleRepository:
    """Repository for vehicle database operations."""
    
    def __init__(self, db: AsyncSession):
        """
        Initialize repository with database session.
        
        Args:
            db: Async database session
        """
        self.db = db
    
    async def create(self, driver_id: UUID, vehicle_data: VehicleCreate) -> Vehicle:
        """
        Create a new vehicle.
        
        Args:
            driver_id: Driver's user ID
            vehicle_data: Vehicle creation data
            
        Returns:
            Created vehicle
        """
        vehicle = Vehicle(
            driver_id=driver_id,
            **vehicle_data.model_dump()
        )
        self.db.add(vehicle)
        await self.db.commit()
        await self.db.refresh(vehicle)
        return vehicle
    
    async def get_by_id(self, vehicle_id: UUID) -> Optional[Vehicle]:
        """
        Get vehicle by ID.
        
        Args:
            vehicle_id: Vehicle ID
            
        Returns:
            Vehicle if found, None otherwise
        """
        result = await self.db.execute(
            select(Vehicle).where(Vehicle.id == vehicle_id)
        )
        return result.scalar_one_or_none()
    
    async def get_by_driver(
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
        query = select(Vehicle).where(Vehicle.driver_id == driver_id)
        
        if not include_inactive:
            query = query.where(Vehicle.is_active == True)
        
        result = await self.db.execute(query.order_by(Vehicle.created_at.desc()))
        return list(result.scalars().all())
    
    async def get_by_plate_number(self, plate_number: str) -> Optional[Vehicle]:
        """
        Get vehicle by plate number.
        
        Args:
            plate_number: Vehicle plate number
            
        Returns:
            Vehicle if found, None otherwise
        """
        result = await self.db.execute(
            select(Vehicle).where(Vehicle.plate_number == plate_number.upper())
        )
        return result.scalar_one_or_none()
    
    async def update(
        self,
        vehicle: Vehicle,
        vehicle_data: VehicleUpdate
    ) -> Vehicle:
        """
        Update vehicle.
        
        Args:
            vehicle: Vehicle to update
            vehicle_data: Update data
            
        Returns:
            Updated vehicle
        """
        update_data = vehicle_data.model_dump(exclude_unset=True)
        
        for field, value in update_data.items():
            setattr(vehicle, field, value)
        
        await self.db.commit()
        await self.db.refresh(vehicle)
        return vehicle
    
    async def delete(self, vehicle: Vehicle) -> None:
        """
        Soft delete vehicle (set is_active to False).
        
        Args:
            vehicle: Vehicle to delete
        """
        vehicle.is_active = False
        await self.db.commit()
    
    async def count_by_driver(self, driver_id: UUID) -> int:
        """
        Count vehicles for a driver.
        
        Args:
            driver_id: Driver's user ID
            
        Returns:
            Number of vehicles
        """
        result = await self.db.execute(
            select(Vehicle).where(
                Vehicle.driver_id == driver_id,
                Vehicle.is_active == True
            )
        )
        return len(list(result.scalars().all()))

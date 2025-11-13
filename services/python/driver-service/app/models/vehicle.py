"""
Vehicle model.
"""
from datetime import datetime
from uuid import uuid4

from sqlalchemy import Column, String, Integer, Boolean, DateTime, Index
from sqlalchemy.dialects.postgresql import UUID
from geoalchemy2 import Geometry

from app.core.database import Base


class Vehicle(Base):
    """Vehicle model for driver's vehicles."""
    
    __tablename__ = "vehicles"
    
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    driver_id = Column(UUID(as_uuid=True), nullable=False, index=True)
    plate_number = Column(String(20), nullable=False, unique=True)
    make = Column(String(100), nullable=False)
    model = Column(String(100), nullable=False)
    year = Column(Integer, nullable=False)
    color = Column(String(50), nullable=False)
    seats_total = Column(Integer, nullable=False)
    vehicle_photo_url = Column(String(500), nullable=True)
    is_verified = Column(Boolean, default=False, nullable=False)
    is_active = Column(Boolean, default=True, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)
    
    __table_args__ = (
        Index('idx_vehicles_driver_active', 'driver_id', 'is_active'),
    )
    
    def __repr__(self):
        return f"<Vehicle {self.plate_number}>"

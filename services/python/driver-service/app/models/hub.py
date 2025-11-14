"""
Hub model for transportation hubs.
"""
from datetime import datetime
from uuid import uuid4

from sqlalchemy import Column, String, Boolean, DateTime, Numeric, Index
from sqlalchemy.dialects.postgresql import UUID
from geoalchemy2 import Geometry

from app.core.database import Base


class Hub(Base):
    """Hub model for route origin points and demand aggregation."""
    
    __tablename__ = "hubs"
    
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    name = Column(String(200), nullable=False)
    lat = Column(Numeric(10, 8), nullable=False)
    lon = Column(Numeric(11, 8), nullable=False)
    location = Column(Geometry('POINT', srid=4326), nullable=False)
    area_id = Column(String(50), nullable=True, index=True)
    zone = Column(String(100), nullable=True)
    is_active = Column(Boolean, default=True, nullable=False, index=True)
    address = Column(String(500), nullable=True)
    landmark = Column(String(200), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)
    
    __table_args__ = (
        Index('idx_hubs_location', 'location', postgresql_using='gist'),
        Index('idx_hubs_area_active', 'area_id', 'is_active'),
    )
    
    def __repr__(self):
        return f"<Hub {self.name} ({self.area_id})>"

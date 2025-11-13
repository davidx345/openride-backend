"""
Stop model.
"""
from datetime import datetime
from uuid import uuid4

from sqlalchemy import Column, String, DateTime, Numeric, Index, UniqueConstraint
from sqlalchemy.dialects.postgresql import UUID
from geoalchemy2 import Geometry

from app.core.database import Base


class Stop(Base):
    """Stop model for route waypoints."""
    
    __tablename__ = "stops"
    
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    name = Column(String(255), nullable=False)
    lat = Column(Numeric(10, 8), nullable=False)
    lon = Column(Numeric(11, 8), nullable=False)
    address = Column(String(500), nullable=True)
    landmark = Column(String(255), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    
    # PostGIS geometry column for geospatial queries
    location = Column(Geometry('POINT', srid=4326), nullable=True)
    
    __table_args__ = (
        Index('idx_stops_location', 'location', postgresql_using='gist'),
        UniqueConstraint('lat', 'lon', name='uq_stops_lat_lon'),
    )
    
    def __repr__(self):
        return f"<Stop {self.name} ({self.lat}, {self.lon})>"

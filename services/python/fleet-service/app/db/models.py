"""SQLAlchemy database models for Fleet Service"""
from datetime import datetime
from decimal import Decimal
from typing import Optional
from uuid import UUID, uuid4

from geoalchemy2 import Geometry
from sqlalchemy import (
    Boolean,
    Column,
    DateTime,
    Enum,
    Integer,
    Numeric,
    String,
    Text,
)
from sqlalchemy.dialects.postgresql import UUID as PGUUID
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.sql import func

Base = declarative_base()


class DriverLocation(Base):
    """Real-time driver location tracking"""
    
    __tablename__ = 'driver_locations'
    
    id = Column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    driver_id = Column(PGUUID(as_uuid=True), nullable=False, index=True)
    location = Column(Geometry(geometry_type='POINT', srid=4326), nullable=False)
    latitude = Column(Numeric(10, 8), nullable=False)
    longitude = Column(Numeric(11, 8), nullable=False)
    bearing = Column(Numeric(5, 2), nullable=True, comment='Direction in degrees (0-360)')
    speed = Column(Numeric(6, 2), nullable=True, comment='Speed in km/h')
    accuracy = Column(Numeric(6, 2), nullable=True, comment='GPS accuracy in meters')
    altitude = Column(Numeric(8, 2), nullable=True, comment='Altitude in meters')
    status = Column(
        Enum('OFFLINE', 'ONLINE', 'BUSY', 'ON_TRIP', name='driver_status'),
        nullable=False,
        server_default='OFFLINE'
    )
    created_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    updated_at = Column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
        onupdate=func.now()
    )
    
    def __repr__(self) -> str:
        return f"<DriverLocation(driver_id={self.driver_id}, lat={self.latitude}, lon={self.longitude})>"


class TripTracking(Base):
    """Trip lifecycle and status tracking"""
    
    __tablename__ = 'trip_tracking'
    
    id = Column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    trip_id = Column(PGUUID(as_uuid=True), nullable=False, unique=True, index=True)
    booking_id = Column(PGUUID(as_uuid=True), nullable=False, index=True)
    driver_id = Column(PGUUID(as_uuid=True), nullable=False, index=True)
    rider_id = Column(PGUUID(as_uuid=True), nullable=False, index=True)
    status = Column(
        Enum('PENDING', 'EN_ROUTE', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', name='trip_status'),
        nullable=False,
        server_default='PENDING',
        index=True
    )
    pickup_location = Column(Geometry(geometry_type='POINT', srid=4326), nullable=False)
    pickup_latitude = Column(Numeric(10, 8), nullable=False)
    pickup_longitude = Column(Numeric(11, 8), nullable=False)
    dropoff_location = Column(Geometry(geometry_type='POINT', srid=4326), nullable=False)
    dropoff_latitude = Column(Numeric(10, 8), nullable=False)
    dropoff_longitude = Column(Numeric(11, 8), nullable=False)
    scheduled_time = Column(DateTime(timezone=True), nullable=False, index=True)
    started_at = Column(DateTime(timezone=True), nullable=True)
    arrived_at = Column(DateTime(timezone=True), nullable=True)
    pickup_time = Column(DateTime(timezone=True), nullable=True)
    completed_at = Column(DateTime(timezone=True), nullable=True)
    cancelled_at = Column(DateTime(timezone=True), nullable=True)
    estimated_arrival = Column(DateTime(timezone=True), nullable=True)
    distance_meters = Column(Integer, nullable=True, comment='Total trip distance')
    duration_seconds = Column(Integer, nullable=True, comment='Total trip duration')
    created_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    updated_at = Column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
        onupdate=func.now()
    )
    
    def __repr__(self) -> str:
        return f"<TripTracking(trip_id={self.trip_id}, status={self.status})>"


class ConnectionSession(Base):
    """WebSocket connection session tracking"""
    
    __tablename__ = 'connection_sessions'
    
    id = Column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    session_id = Column(String(100), nullable=False, unique=True, index=True)
    user_id = Column(PGUUID(as_uuid=True), nullable=False, index=True)
    user_role = Column(String(20), nullable=False, comment='DRIVER or RIDER')
    connection_type = Column(String(20), nullable=False, comment='WEBSOCKET or POLLING')
    ip_address = Column(String(45), nullable=True)
    user_agent = Column(Text, nullable=True)
    connected_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    disconnected_at = Column(DateTime(timezone=True), nullable=True)
    last_activity = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    is_active = Column(Boolean, nullable=False, server_default='true')
    
    def __repr__(self) -> str:
        return f"<ConnectionSession(user_id={self.user_id}, role={self.user_role}, active={self.is_active})>"


class LocationHistory(Base):
    """Archived driver location history"""
    
    __tablename__ = 'location_history'
    
    id = Column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    driver_id = Column(PGUUID(as_uuid=True), nullable=False, index=True)
    trip_id = Column(PGUUID(as_uuid=True), nullable=True, index=True)
    location = Column(Geometry(geometry_type='POINT', srid=4326), nullable=False)
    latitude = Column(Numeric(10, 8), nullable=False)
    longitude = Column(Numeric(11, 8), nullable=False)
    bearing = Column(Numeric(5, 2), nullable=True)
    speed = Column(Numeric(6, 2), nullable=True)
    accuracy = Column(Numeric(6, 2), nullable=True)
    recorded_at = Column(DateTime(timezone=True), nullable=False, index=True)
    created_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    
    def __repr__(self) -> str:
        return f"<LocationHistory(driver_id={self.driver_id}, recorded_at={self.recorded_at})>"

"""
Route and RouteStop models.
"""
from datetime import datetime, time
from uuid import uuid4

from sqlalchemy import (
    Column, String, Integer, Boolean, DateTime, Time, 
    Enum, ForeignKey, CheckConstraint, Index, Numeric
)
from sqlalchemy.dialects.postgresql import UUID, ARRAY
from sqlalchemy.orm import relationship
import enum

from app.core.database import Base


class RouteStatus(str, enum.Enum):
    """Route status enumeration."""
    ACTIVE = "ACTIVE"
    PAUSED = "PAUSED"
    CANCELLED = "CANCELLED"


class Route(Base):
    """Route model for driver's fixed routes."""
    
    __tablename__ = "routes"
    
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    driver_id = Column(UUID(as_uuid=True), nullable=False, index=True)
    vehicle_id = Column(UUID(as_uuid=True), ForeignKey("vehicles.id"), nullable=False)
    name = Column(String(255), nullable=False)
    departure_time = Column(Time, nullable=False)
    active_days = Column(ARRAY(Integer), nullable=False)  # [0=Mon, 1=Tue, ..., 6=Sun]
    schedule_rrule = Column(String(500), nullable=True)
    seats_total = Column(Integer, nullable=False)
    seats_available = Column(Integer, nullable=False)
    base_price = Column(Numeric(10, 2), nullable=False)
    status = Column(
        Enum(RouteStatus, native_enum=False),
        default=RouteStatus.ACTIVE,
        nullable=False,
        index=True
    )
    notes = Column(String(1000), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)
    
    # Relationships
    vehicle = relationship("Vehicle", foreign_keys=[vehicle_id])
    route_stops = relationship("RouteStop", back_populates="route", cascade="all, delete-orphan")
    
    __table_args__ = (
        CheckConstraint('seats_available >= 0 AND seats_available <= seats_total', name='ck_seats_valid'),
        CheckConstraint('base_price >= 0', name='ck_price_positive'),
        Index('idx_routes_driver_status', 'driver_id', 'status'),
        Index('idx_routes_status_departure', 'status', 'departure_time'),
    )
    
    def __repr__(self):
        return f"<Route {self.name} - {self.status.value}>"


class RouteStop(Base):
    """RouteStop model for route waypoints with order."""
    
    __tablename__ = "route_stops"
    
    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    route_id = Column(UUID(as_uuid=True), ForeignKey("routes.id", ondelete="CASCADE"), nullable=False)
    stop_id = Column(UUID(as_uuid=True), ForeignKey("stops.id"), nullable=False)
    stop_order = Column(Integer, nullable=False)
    planned_arrival_offset_minutes = Column(Integer, nullable=False)
    price_from_origin = Column(Numeric(10, 2), nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    
    # Relationships
    route = relationship("Route", back_populates="route_stops")
    stop = relationship("Stop")
    
    __table_args__ = (
        CheckConstraint('stop_order >= 0', name='ck_stop_order_positive'),
        CheckConstraint('planned_arrival_offset_minutes >= 0', name='ck_arrival_offset_positive'),
        CheckConstraint('price_from_origin >= 0', name='ck_price_positive'),
        Index('idx_route_stops_route_order', 'route_id', 'stop_order'),
    )
    
    def __repr__(self):
        return f"<RouteStop route={self.route_id} order={self.stop_order}>"

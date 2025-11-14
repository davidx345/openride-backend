"""Route model with geospatial fields."""

from datetime import time
from decimal import Decimal
from enum import Enum
from typing import TYPE_CHECKING
from uuid import UUID, uuid4

from geoalchemy2 import Geometry
from sqlalchemy import (
    ARRAY,
    CheckConstraint,
    ForeignKey,
    Integer,
    Numeric,
    String,
    Time,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base
from app.models.mixins import TimestampMixin

if TYPE_CHECKING:
    from app.models.route_stop import RouteStop


class RouteStatus(str, Enum):
    """Route status enumeration."""

    ACTIVE = "ACTIVE"
    PAUSED = "PAUSED"
    CANCELLED = "CANCELLED"


class Route(Base, TimestampMixin):
    """Route model with geospatial data."""

    __tablename__ = "routes"

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    driver_id: Mapped[UUID] = mapped_column(nullable=False, index=True)
    vehicle_id: Mapped[UUID] = mapped_column(nullable=False)
    name: Mapped[str] = mapped_column(String(200), nullable=False)

    # Time and Schedule
    departure_time: Mapped[time] = mapped_column(Time, nullable=False)
    active_days: Mapped[list[int]] = mapped_column(
        ARRAY(item_type=int), nullable=False
    )  # 0=Mon, 6=Sun
    schedule_rrule: Mapped[str | None] = mapped_column(String(500))

    # Geospatial fields - Start Location
    start_lat: Mapped[Decimal | None] = mapped_column(Numeric(precision=10, scale=8))
    start_lon: Mapped[Decimal | None] = mapped_column(Numeric(precision=11, scale=8))
    start_location: Mapped[bytes | None] = mapped_column(
        Geometry(geometry_type="POINT", srid=4326)
    )

    # Geospatial fields - End Location
    end_lat: Mapped[Decimal | None] = mapped_column(Numeric(precision=10, scale=8))
    end_lon: Mapped[Decimal | None] = mapped_column(Numeric(precision=11, scale=8))
    end_location: Mapped[bytes | None] = mapped_column(
        Geometry(geometry_type="POINT", srid=4326)
    )

    # Capacity and Pricing
    seats_total: Mapped[int] = mapped_column(nullable=False)
    seats_available: Mapped[int] = mapped_column(nullable=False)
    base_price: Mapped[Decimal] = mapped_column(Numeric(precision=10, scale=2), nullable=False)

    # Status
    status: Mapped[RouteStatus] = mapped_column(
        String(20), nullable=False, default=RouteStatus.ACTIVE, index=True
    )

    # Additional
    notes: Mapped[str | None] = mapped_column(String(1000))

    # Hub association (Phase 1.3)
    origin_hub_id: Mapped[UUID | None] = mapped_column(
        ForeignKey("hubs.id", ondelete="SET NULL"), index=True
    )
    destination_hub_id: Mapped[UUID | None] = mapped_column(
        ForeignKey("hubs.id", ondelete="SET NULL"), index=True
    )
    currency: Mapped[str] = mapped_column(String(3), default="NGN", nullable=False)
    estimated_duration_minutes: Mapped[int | None] = mapped_column(Integer)
    route_template_id: Mapped[UUID | None] = mapped_column(index=True)

    # Relationships
    route_stops: Mapped[list["RouteStop"]] = relationship(
        "RouteStop",
        back_populates="route",
        cascade="all, delete-orphan",
        order_by="RouteStop.stop_order",
    )

    # Constraints
    __table_args__ = (
        CheckConstraint("seats_available >= 0", name="check_seats_available_non_negative"),
        CheckConstraint("seats_available <= seats_total", name="check_seats_available_lte_total"),
        CheckConstraint("base_price >= 0", name="check_base_price_non_negative"),
        CheckConstraint(
            "array_length(active_days, 1) > 0", name="check_active_days_not_empty"
        ),
    )

    def __repr__(self) -> str:
        """String representation."""
        return f"<Route(id={self.id}, name={self.name}, hubs={self.origin_hub_id}->{self.destination_hub_id})>"

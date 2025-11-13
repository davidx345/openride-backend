"""RouteStop association model."""

from decimal import Decimal
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import CheckConstraint, ForeignKey, Numeric
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.database import Base

if TYPE_CHECKING:
    from app.models.route import Route
    from app.models.stop import Stop


class RouteStop(Base):
    """Association table for routes and stops with ordering and pricing."""

    __tablename__ = "route_stops"

    route_id: Mapped[UUID] = mapped_column(
        ForeignKey("routes.id", ondelete="CASCADE"), primary_key=True
    )
    stop_id: Mapped[UUID] = mapped_column(ForeignKey("stops.id"), primary_key=True)

    # Stop ordering in route
    stop_order: Mapped[int] = mapped_column(nullable=False)

    # Time offset from departure (in minutes)
    planned_arrival_offset_minutes: Mapped[int] = mapped_column(nullable=False, default=0)

    # Price from origin to this stop
    price_from_origin: Mapped[Decimal] = mapped_column(
        Numeric(precision=10, scale=2), nullable=False, default=0
    )

    # Relationships
    route: Mapped["Route"] = relationship("Route", back_populates="route_stops")
    stop: Mapped["Stop"] = relationship("Stop")

    # Constraints
    __table_args__ = (
        CheckConstraint("stop_order >= 0", name="check_stop_order_non_negative"),
        CheckConstraint(
            "planned_arrival_offset_minutes >= 0", name="check_arrival_offset_non_negative"
        ),
        CheckConstraint("price_from_origin >= 0", name="check_price_from_origin_non_negative"),
    )

    def __repr__(self) -> str:
        """String representation."""
        return (
            f"<RouteStop(route_id={self.route_id}, stop_id={self.stop_id}, "
            f"order={self.stop_order})>"
        )

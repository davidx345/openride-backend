"""Stop model for route waypoints."""

from decimal import Decimal
from uuid import UUID, uuid4

from geoalchemy2 import Geometry
from sqlalchemy import Numeric, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base
from app.models.mixins import TimestampMixin


class Stop(Base, TimestampMixin):
    """Stop/waypoint model with geospatial location."""

    __tablename__ = "stops"

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    name: Mapped[str] = mapped_column(String(200), nullable=False)

    # Coordinates
    lat: Mapped[Decimal] = mapped_column(Numeric(precision=10, scale=8), nullable=False)
    lon: Mapped[Decimal] = mapped_column(Numeric(precision=11, scale=8), nullable=False)

    # PostGIS geometry column (auto-computed from lat/lon)
    location: Mapped[bytes] = mapped_column(
        Geometry(geometry_type="POINT", srid=4326), nullable=False
    )

    # Address details
    address: Mapped[str | None] = mapped_column(String(500))
    landmark: Mapped[str | None] = mapped_column(String(200))

    # Constraints
    __table_args__ = (
        UniqueConstraint("lat", "lon", name="uq_stop_coordinates"),
    )

    def __repr__(self) -> str:
        """String representation."""
        return f"<Stop(id={self.id}, name={self.name}, lat={self.lat}, lon={self.lon})>"

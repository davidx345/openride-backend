"""Hub model for route matching service."""

from decimal import Decimal
from uuid import UUID, uuid4

from geoalchemy2 import Geometry
from sqlalchemy import Boolean, Numeric, String
from sqlalchemy.orm import Mapped, mapped_column

from app.core.database import Base
from app.models.mixins import TimestampMixin


class Hub(Base, TimestampMixin):
    """Hub model for transportation hubs and route origins."""

    __tablename__ = "hubs"

    id: Mapped[UUID] = mapped_column(primary_key=True, default=uuid4)
    name: Mapped[str] = mapped_column(String(200), nullable=False)

    # Coordinates
    lat: Mapped[Decimal] = mapped_column(Numeric(precision=10, scale=8), nullable=False)
    lon: Mapped[Decimal] = mapped_column(Numeric(precision=11, scale=8), nullable=False)

    # PostGIS geometry column
    location: Mapped[bytes] = mapped_column(
        Geometry(geometry_type="POINT", srid=4326), nullable=False
    )

    # Metadata
    area_id: Mapped[str | None] = mapped_column(String(50), index=True)
    zone: Mapped[str | None] = mapped_column(String(100))
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False, index=True)
    address: Mapped[str | None] = mapped_column(String(500))
    landmark: Mapped[str | None] = mapped_column(String(200))

    def __repr__(self) -> str:
        """String representation."""
        return f"<Hub(id={self.id}, name={self.name}, area={self.area_id})>"

"""Initial migration - create vehicles, stops, routes tables

Revision ID: 001
Revises: 
Create Date: 2024-01-15 10:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql
from geoalchemy2 import Geometry

# revision identifiers, used by Alembic.
revision: str = '001'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Create tables for driver service."""
    
    # Create enum types
    op.execute("CREATE TYPE route_status AS ENUM ('ACTIVE', 'PAUSED', 'CANCELLED')")
    
    # Create vehicles table
    op.create_table(
        'vehicles',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column('driver_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('plate_number', sa.String(20), nullable=False, unique=True),
        sa.Column('make', sa.String(100), nullable=False),
        sa.Column('model', sa.String(100), nullable=False),
        sa.Column('year', sa.Integer, nullable=False),
        sa.Column('color', sa.String(50), nullable=False),
        sa.Column('seats_total', sa.Integer, nullable=False),
        sa.Column('vehicle_photo_url', sa.String(500), nullable=True),
        sa.Column('is_verified', sa.Boolean, default=False, nullable=False),
        sa.Column('is_active', sa.Boolean, default=True, nullable=False),
        sa.Column('created_at', sa.DateTime, server_default=sa.text('NOW()'), nullable=False),
        sa.Column('updated_at', sa.DateTime, server_default=sa.text('NOW()'), nullable=False),
    )
    
    # Create indexes for vehicles
    op.create_index('idx_vehicles_driver', 'vehicles', ['driver_id'])
    op.create_index('idx_vehicles_driver_active', 'vehicles', ['driver_id', 'is_active'])
    
    # Create stops table
    op.create_table(
        'stops',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column('name', sa.String(255), nullable=False),
        sa.Column('lat', sa.Numeric(10, 8), nullable=False),
        sa.Column('lon', sa.Numeric(11, 8), nullable=False),
        sa.Column('address', sa.String(500), nullable=True),
        sa.Column('landmark', sa.String(255), nullable=True),
        sa.Column('created_at', sa.DateTime, server_default=sa.text('NOW()'), nullable=False),
    )
    
    # Add PostGIS geometry column
    op.execute("""
        ALTER TABLE stops 
        ADD COLUMN location geometry(POINT, 4326)
    """)
    
    # Create unique constraint on lat/lon
    op.create_unique_constraint('uq_stops_lat_lon', 'stops', ['lat', 'lon'])
    
    # Create spatial index
    op.execute("""
        CREATE INDEX idx_stops_location 
        ON stops 
        USING GIST (location)
    """)
    
    # Create routes table
    op.create_table(
        'routes',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column('driver_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('vehicle_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('name', sa.String(255), nullable=False),
        sa.Column('departure_time', sa.Time, nullable=False),
        sa.Column('active_days', postgresql.ARRAY(sa.Integer), nullable=False),
        sa.Column('schedule_rrule', sa.String(500), nullable=True),
        sa.Column('seats_total', sa.Integer, nullable=False),
        sa.Column('seats_available', sa.Integer, nullable=False),
        sa.Column('base_price', sa.Numeric(10, 2), nullable=False),
        sa.Column('status', sa.Enum('ACTIVE', 'PAUSED', 'CANCELLED', name='route_status'), nullable=False),
        sa.Column('notes', sa.String(1000), nullable=True),
        sa.Column('created_at', sa.DateTime, server_default=sa.text('NOW()'), nullable=False),
        sa.Column('updated_at', sa.DateTime, server_default=sa.text('NOW()'), nullable=False),
        sa.ForeignKeyConstraint(['vehicle_id'], ['vehicles.id']),
        sa.CheckConstraint('seats_available >= 0 AND seats_available <= seats_total', name='ck_seats_valid'),
        sa.CheckConstraint('base_price >= 0', name='ck_price_positive'),
    )
    
    # Create indexes for routes
    op.create_index('idx_routes_driver', 'routes', ['driver_id'])
    op.create_index('idx_routes_status', 'routes', ['status'])
    op.create_index('idx_routes_driver_status', 'routes', ['driver_id', 'status'])
    op.create_index('idx_routes_status_departure', 'routes', ['status', 'departure_time'])
    
    # Create route_stops table
    op.create_table(
        'route_stops',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True),
        sa.Column('route_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('stop_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('stop_order', sa.Integer, nullable=False),
        sa.Column('planned_arrival_offset_minutes', sa.Integer, nullable=False),
        sa.Column('price_from_origin', sa.Numeric(10, 2), nullable=False),
        sa.Column('created_at', sa.DateTime, server_default=sa.text('NOW()'), nullable=False),
        sa.ForeignKeyConstraint(['route_id'], ['routes.id'], ondelete='CASCADE'),
        sa.ForeignKeyConstraint(['stop_id'], ['stops.id']),
        sa.CheckConstraint('stop_order >= 0', name='ck_stop_order_positive'),
        sa.CheckConstraint('planned_arrival_offset_minutes >= 0', name='ck_arrival_offset_positive'),
        sa.CheckConstraint('price_from_origin >= 0', name='ck_price_positive'),
    )
    
    # Create indexes for route_stops
    op.create_index('idx_route_stops_route_order', 'route_stops', ['route_id', 'stop_order'])


def downgrade() -> None:
    """Drop all tables."""
    
    op.drop_table('route_stops')
    op.drop_table('routes')
    op.drop_table('stops')
    op.drop_table('vehicles')
    
    # Drop enum type
    op.execute("DROP TYPE route_status")

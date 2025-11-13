"""Initial fleet service schema

Revision ID: 001
Revises: 
Create Date: 2024-12-15 10:00:00.000000

"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql
from geoalchemy2 import Geometry

# revision identifiers
revision = '001'
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    # Enable PostGIS extension
    op.execute('CREATE EXTENSION IF NOT EXISTS postgis;')
    
    # Create ENUM types
    op.execute("""
        DO $$ BEGIN
            CREATE TYPE trip_status AS ENUM (
                'PENDING',
                'EN_ROUTE',
                'ARRIVED',
                'IN_PROGRESS',
                'COMPLETED',
                'CANCELLED'
            );
        EXCEPTION
            WHEN duplicate_object THEN null;
        END $$;
    """)
    
    op.execute("""
        DO $$ BEGIN
            CREATE TYPE driver_status AS ENUM (
                'OFFLINE',
                'ONLINE',
                'BUSY',
                'ON_TRIP'
            );
        EXCEPTION
            WHEN duplicate_object THEN null;
        END $$;
    """)
    
    # Create driver_locations table
    op.create_table(
        'driver_locations',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text('gen_random_uuid()')),
        sa.Column('driver_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('location', Geometry(geometry_type='POINT', srid=4326), nullable=False),
        sa.Column('latitude', sa.DECIMAL(10, 8), nullable=False),
        sa.Column('longitude', sa.DECIMAL(11, 8), nullable=False),
        sa.Column('bearing', sa.DECIMAL(5, 2), nullable=True, comment='Direction in degrees (0-360)'),
        sa.Column('speed', sa.DECIMAL(6, 2), nullable=True, comment='Speed in km/h'),
        sa.Column('accuracy', sa.DECIMAL(6, 2), nullable=True, comment='GPS accuracy in meters'),
        sa.Column('altitude', sa.DECIMAL(8, 2), nullable=True, comment='Altitude in meters'),
        sa.Column('status', sa.Enum('OFFLINE', 'ONLINE', 'BUSY', 'ON_TRIP', name='driver_status'), nullable=False, server_default='OFFLINE'),
        sa.Column('created_at', sa.TIMESTAMP(timezone=True), nullable=False, server_default=sa.text('CURRENT_TIMESTAMP')),
        sa.Column('updated_at', sa.TIMESTAMP(timezone=True), nullable=False, server_default=sa.text('CURRENT_TIMESTAMP'), onupdate=sa.text('CURRENT_TIMESTAMP'))
    )
    
    # Create indexes for driver_locations
    op.create_index('idx_driver_locations_driver_id', 'driver_locations', ['driver_id'])
    op.create_index('idx_driver_locations_created_at', 'driver_locations', ['created_at'])
    op.create_index('idx_driver_locations_driver_status', 'driver_locations', ['driver_id', 'status'])
    op.execute('CREATE INDEX idx_driver_locations_location ON driver_locations USING GIST (location);')
    
    # Create trip_tracking table
    op.create_table(
        'trip_tracking',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text('gen_random_uuid()')),
        sa.Column('trip_id', postgresql.UUID(as_uuid=True), nullable=False, unique=True),
        sa.Column('booking_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('driver_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('rider_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('status', sa.Enum('PENDING', 'EN_ROUTE', 'ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', name='trip_status'), nullable=False, server_default='PENDING'),
        sa.Column('pickup_location', Geometry(geometry_type='POINT', srid=4326), nullable=False),
        sa.Column('pickup_latitude', sa.DECIMAL(10, 8), nullable=False),
        sa.Column('pickup_longitude', sa.DECIMAL(11, 8), nullable=False),
        sa.Column('dropoff_location', Geometry(geometry_type='POINT', srid=4326), nullable=False),
        sa.Column('dropoff_latitude', sa.DECIMAL(10, 8), nullable=False),
        sa.Column('dropoff_longitude', sa.DECIMAL(11, 8), nullable=False),
        sa.Column('scheduled_time', sa.TIMESTAMP(timezone=True), nullable=False),
        sa.Column('started_at', sa.TIMESTAMP(timezone=True), nullable=True),
        sa.Column('arrived_at', sa.TIMESTAMP(timezone=True), nullable=True),
        sa.Column('pickup_time', sa.TIMESTAMP(timezone=True), nullable=True),
        sa.Column('completed_at', sa.TIMESTAMP(timezone=True), nullable=True),
        sa.Column('cancelled_at', sa.TIMESTAMP(timezone=True), nullable=True),
        sa.Column('estimated_arrival', sa.TIMESTAMP(timezone=True), nullable=True),
        sa.Column('distance_meters', sa.Integer, nullable=True, comment='Total trip distance'),
        sa.Column('duration_seconds', sa.Integer, nullable=True, comment='Total trip duration'),
        sa.Column('created_at', sa.TIMESTAMP(timezone=True), nullable=False, server_default=sa.text('CURRENT_TIMESTAMP')),
        sa.Column('updated_at', sa.TIMESTAMP(timezone=True), nullable=False, server_default=sa.text('CURRENT_TIMESTAMP'), onupdate=sa.text('CURRENT_TIMESTAMP'))
    )
    
    # Create indexes for trip_tracking
    op.create_index('idx_trip_tracking_trip_id', 'trip_tracking', ['trip_id'])
    op.create_index('idx_trip_tracking_booking_id', 'trip_tracking', ['booking_id'])
    op.create_index('idx_trip_tracking_driver_id', 'trip_tracking', ['driver_id'])
    op.create_index('idx_trip_tracking_rider_id', 'trip_tracking', ['rider_id'])
    op.create_index('idx_trip_tracking_status', 'trip_tracking', ['status'])
    op.create_index('idx_trip_tracking_scheduled_time', 'trip_tracking', ['scheduled_time'])
    op.execute('CREATE INDEX idx_trip_tracking_pickup ON trip_tracking USING GIST (pickup_location);')
    op.execute('CREATE INDEX idx_trip_tracking_dropoff ON trip_tracking USING GIST (dropoff_location);')
    
    # Create connection_sessions table
    op.create_table(
        'connection_sessions',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text('gen_random_uuid()')),
        sa.Column('session_id', sa.String(100), nullable=False, unique=True),
        sa.Column('user_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('user_role', sa.String(20), nullable=False, comment='DRIVER or RIDER'),
        sa.Column('connection_type', sa.String(20), nullable=False, comment='WEBSOCKET or POLLING'),
        sa.Column('ip_address', sa.String(45), nullable=True),
        sa.Column('user_agent', sa.Text, nullable=True),
        sa.Column('connected_at', sa.TIMESTAMP(timezone=True), nullable=False, server_default=sa.text('CURRENT_TIMESTAMP')),
        sa.Column('disconnected_at', sa.TIMESTAMP(timezone=True), nullable=True),
        sa.Column('last_activity', sa.TIMESTAMP(timezone=True), nullable=False, server_default=sa.text('CURRENT_TIMESTAMP')),
        sa.Column('is_active', sa.Boolean, nullable=False, server_default='true')
    )
    
    # Create indexes for connection_sessions
    op.create_index('idx_connection_sessions_user_id', 'connection_sessions', ['user_id'])
    op.create_index('idx_connection_sessions_session_id', 'connection_sessions', ['session_id'])
    op.create_index('idx_connection_sessions_active', 'connection_sessions', ['user_id', 'is_active'])
    
    # Create location_history table (for archiving old locations)
    op.create_table(
        'location_history',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text('gen_random_uuid()')),
        sa.Column('driver_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('trip_id', postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column('location', Geometry(geometry_type='POINT', srid=4326), nullable=False),
        sa.Column('latitude', sa.DECIMAL(10, 8), nullable=False),
        sa.Column('longitude', sa.DECIMAL(11, 8), nullable=False),
        sa.Column('bearing', sa.DECIMAL(5, 2), nullable=True),
        sa.Column('speed', sa.DECIMAL(6, 2), nullable=True),
        sa.Column('accuracy', sa.DECIMAL(6, 2), nullable=True),
        sa.Column('recorded_at', sa.TIMESTAMP(timezone=True), nullable=False),
        sa.Column('created_at', sa.TIMESTAMP(timezone=True), nullable=False, server_default=sa.text('CURRENT_TIMESTAMP'))
    )
    
    # Create indexes for location_history (optimized for time-series queries)
    op.create_index('idx_location_history_driver_recorded', 'location_history', ['driver_id', 'recorded_at'])
    op.create_index('idx_location_history_trip', 'location_history', ['trip_id', 'recorded_at'])
    op.execute('CREATE INDEX idx_location_history_location ON location_history USING GIST (location);')
    
    # Create function to automatically update updated_at timestamp
    op.execute("""
        CREATE OR REPLACE FUNCTION update_updated_at_column()
        RETURNS TRIGGER AS $$
        BEGIN
            NEW.updated_at = CURRENT_TIMESTAMP;
            RETURN NEW;
        END;
        $$ language 'plpgsql';
    """)
    
    # Create triggers for updated_at
    op.execute("""
        CREATE TRIGGER update_driver_locations_updated_at
        BEFORE UPDATE ON driver_locations
        FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    """)
    
    op.execute("""
        CREATE TRIGGER update_trip_tracking_updated_at
        BEFORE UPDATE ON trip_tracking
        FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
    """)
    
    # Create function to archive old locations
    op.execute("""
        CREATE OR REPLACE FUNCTION archive_old_driver_locations()
        RETURNS void AS $$
        BEGIN
            INSERT INTO location_history (
                driver_id, trip_id, location, latitude, longitude,
                bearing, speed, accuracy, recorded_at
            )
            SELECT 
                driver_id, NULL, location, latitude, longitude,
                bearing, speed, accuracy, created_at
            FROM driver_locations
            WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '7 days';
            
            DELETE FROM driver_locations
            WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '7 days';
        END;
        $$ LANGUAGE plpgsql;
    """)


def downgrade() -> None:
    # Drop triggers
    op.execute('DROP TRIGGER IF EXISTS update_trip_tracking_updated_at ON trip_tracking;')
    op.execute('DROP TRIGGER IF EXISTS update_driver_locations_updated_at ON driver_locations;')
    
    # Drop functions
    op.execute('DROP FUNCTION IF EXISTS update_updated_at_column();')
    op.execute('DROP FUNCTION IF EXISTS archive_old_driver_locations();')
    
    # Drop tables
    op.drop_table('location_history')
    op.drop_table('connection_sessions')
    op.drop_table('trip_tracking')
    op.drop_table('driver_locations')
    
    # Drop ENUM types
    op.execute('DROP TYPE IF EXISTS driver_status;')
    op.execute('DROP TYPE IF EXISTS trip_status;')

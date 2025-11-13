"""Add geospatial columns to routes table

Revision ID: 001
Revises: 
Create Date: 2024-01-15 10:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy import text

# revision identifiers, used by Alembic.
revision: str = '001'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Add geospatial columns to routes table."""
    # Add coordinate columns
    op.add_column('routes', sa.Column('start_lat', sa.Numeric(precision=10, scale=8), nullable=True))
    op.add_column('routes', sa.Column('start_lon', sa.Numeric(precision=11, scale=8), nullable=True))
    op.add_column('routes', sa.Column('end_lat', sa.Numeric(precision=10, scale=8), nullable=True))
    op.add_column('routes', sa.Column('end_lon', sa.Numeric(precision=11, scale=8), nullable=True))

    # Add PostGIS geometry columns using raw SQL
    connection = op.get_bind()
    
    connection.execute(text("""
        ALTER TABLE routes 
        ADD COLUMN start_location geometry(POINT, 4326)
    """))
    
    connection.execute(text("""
        ALTER TABLE routes 
        ADD COLUMN end_location geometry(POINT, 4326)
    """))

    # Create spatial indexes
    op.create_index(
        'idx_routes_start_location',
        'routes',
        ['start_location'],
        postgresql_using='gist'
    )
    
    op.create_index(
        'idx_routes_end_location',
        'routes',
        ['end_location'],
        postgresql_using='gist'
    )

    # Create trigger function to auto-update geometry columns
    connection.execute(text("""
        CREATE OR REPLACE FUNCTION update_route_geometries()
        RETURNS TRIGGER AS $$
        BEGIN
            IF NEW.start_lat IS NOT NULL AND NEW.start_lon IS NOT NULL THEN
                NEW.start_location = ST_SetSRID(ST_MakePoint(NEW.start_lon, NEW.start_lat), 4326);
            END IF;
            
            IF NEW.end_lat IS NOT NULL AND NEW.end_lon IS NOT NULL THEN
                NEW.end_location = ST_SetSRID(ST_MakePoint(NEW.end_lon, NEW.end_lat), 4326);
            END IF;
            
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;
    """))

    # Create trigger
    connection.execute(text("""
        CREATE TRIGGER trg_update_route_geometries
        BEFORE INSERT OR UPDATE ON routes
        FOR EACH ROW
        EXECUTE FUNCTION update_route_geometries();
    """))

    # Update existing routes with start/end coordinates from their first/last stops
    connection.execute(text("""
        WITH route_start_stops AS (
            SELECT DISTINCT ON (rs.route_id)
                rs.route_id,
                s.lat AS start_lat,
                s.lon AS start_lon
            FROM route_stops rs
            JOIN stops s ON rs.stop_id = s.id
            ORDER BY rs.route_id, rs.stop_order ASC
        ),
        route_end_stops AS (
            SELECT DISTINCT ON (rs.route_id)
                rs.route_id,
                s.lat AS end_lat,
                s.lon AS end_lon
            FROM route_stops rs
            JOIN stops s ON rs.stop_id = s.id
            ORDER BY rs.route_id, rs.stop_order DESC
        )
        UPDATE routes r
        SET 
            start_lat = rss.start_lat,
            start_lon = rss.start_lon,
            end_lat = res.end_lat,
            end_lon = res.end_lon
        FROM route_start_stops rss
        JOIN route_end_stops res ON rss.route_id = res.route_id
        WHERE r.id = rss.route_id;
    """))


def downgrade() -> None:
    """Remove geospatial columns from routes table."""
    connection = op.get_bind()
    
    # Drop trigger
    connection.execute(text("DROP TRIGGER IF EXISTS trg_update_route_geometries ON routes"))
    
    # Drop trigger function
    connection.execute(text("DROP FUNCTION IF EXISTS update_route_geometries()"))
    
    # Drop indexes
    op.drop_index('idx_routes_end_location', table_name='routes')
    op.drop_index('idx_routes_start_location', table_name='routes')
    
    # Drop geometry columns
    op.drop_column('routes', 'end_location')
    op.drop_column('routes', 'start_location')
    
    # Drop coordinate columns
    op.drop_column('routes', 'end_lon')
    op.drop_column('routes', 'end_lat')
    op.drop_column('routes', 'start_lon')
    op.drop_column('routes', 'start_lat')

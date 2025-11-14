"""Alembic migration: Create hubs table.

Revision ID: 002_create_hubs_table
Revises: 001_initial_migration
Create Date: 2025-11-14

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
import geoalchemy2
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = '002_create_hubs_table'
down_revision: Union[str, None] = '001_initial_migration'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Create hubs table and related infrastructure."""
    # Create hubs table
    op.create_table(
        'hubs',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text('gen_random_uuid()')),
        sa.Column('name', sa.String(length=200), nullable=False),
        sa.Column('lat', sa.Numeric(precision=10, scale=8), nullable=False),
        sa.Column('lon', sa.Numeric(precision=11, scale=8), nullable=False),
        sa.Column('location', geoalchemy2.types.Geometry(geometry_type='POINT', srid=4326), nullable=False),
        sa.Column('area_id', sa.String(length=50), nullable=True),
        sa.Column('zone', sa.String(length=100), nullable=True),
        sa.Column('is_active', sa.Boolean(), nullable=False, server_default=sa.text('true')),
        sa.Column('address', sa.String(length=500), nullable=True),
        sa.Column('landmark', sa.String(length=200), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('now()')),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('now()')),
        sa.UniqueConstraint('lat', 'lon', name='uq_hub_coordinates')
    )

    # Create indexes
    op.create_index('idx_hubs_location', 'hubs', ['location'], postgresql_using='gist')
    op.create_index('idx_hubs_area', 'hubs', ['area_id'])
    op.create_index('idx_hubs_zone', 'hubs', ['zone'])
    op.create_index('idx_hubs_active', 'hubs', ['is_active'])
    op.create_index('idx_hubs_area_active', 'hubs', ['area_id', 'is_active'])

    # Create trigger function to auto-update location from lat/lon
    op.execute("""
        CREATE OR REPLACE FUNCTION update_hub_location()
        RETURNS TRIGGER AS $$
        BEGIN
            NEW.location = ST_SetSRID(ST_MakePoint(NEW.lon, NEW.lat), 4326);
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;
    """)

    # Create trigger
    op.execute("""
        CREATE TRIGGER trg_hubs_location
            BEFORE INSERT OR UPDATE OF lat, lon ON hubs
            FOR EACH ROW
            EXECUTE FUNCTION update_hub_location();
    """)

    # Create trigger function to update updated_at
    op.execute("""
        CREATE OR REPLACE FUNCTION update_hubs_updated_at()
        RETURNS TRIGGER AS $$
        BEGIN
            NEW.updated_at = NOW();
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;
    """)

    # Create updated_at trigger
    op.execute("""
        CREATE TRIGGER trg_hubs_updated_at
            BEFORE UPDATE ON hubs
            FOR EACH ROW
            EXECUTE FUNCTION update_hubs_updated_at();
    """)

    # Seed initial Lagos hubs
    op.execute("""
        INSERT INTO hubs (name, lat, lon, area_id, zone, address, landmark) VALUES
            ('Victoria Island Hub', 6.4281, 3.4219, 'VI', 'Island', 'Ahmadu Bello Way, Victoria Island', 'Eko Hotel'),
            ('Lekki Phase 1 Hub', 6.4474, 3.4780, 'Lekki', 'Island', 'Admiralty Way, Lekki Phase 1', 'Lekki Toll Gate'),
            ('Ikeja Hub', 6.5964, 3.3374, 'Ikeja', 'Mainland', 'Allen Avenue, Ikeja', 'Computer Village'),
            ('Yaba Hub', 6.5158, 3.3696, 'Yaba', 'Mainland', 'Herbert Macaulay Way, Yaba', 'Yaba Tech'),
            ('Surulere Hub', 6.4969, 3.3603, 'Surulere', 'Mainland', 'Adeniran Ogunsanya Street', 'National Stadium'),
            ('Ikoyi Hub', 6.4541, 3.4316, 'Ikoyi', 'Island', 'Awolowo Road, Ikoyi', 'Falomo Roundabout'),
            ('Maryland Hub', 6.5773, 3.3598, 'Maryland', 'Mainland', 'Ikorodu Road, Maryland', 'Maryland Mall'),
            ('Ajah Hub', 6.4698, 3.5651, 'Ajah', 'Island', 'Lekki-Epe Expressway, Ajah', 'Ajah Roundabout'),
            ('Obalende Hub', 6.4487, 3.3951, 'Obalende', 'Island', 'Obalende Road', 'Obalende Bus Stop'),
            ('Oshodi Hub', 6.5450, 3.3340, 'Oshodi', 'Mainland', 'Agege Motor Road, Oshodi', 'Oshodi Interchange');
    """)


def downgrade() -> None:
    """Drop hubs table and related infrastructure."""
    # Drop triggers
    op.execute('DROP TRIGGER IF EXISTS trg_hubs_updated_at ON hubs;')
    op.execute('DROP TRIGGER IF EXISTS trg_hubs_location ON hubs;')
    
    # Drop functions
    op.execute('DROP FUNCTION IF EXISTS update_hubs_updated_at();')
    op.execute('DROP FUNCTION IF EXISTS update_hub_location();')
    
    # Drop indexes (will be dropped automatically with table, but explicit for clarity)
    op.drop_index('idx_hubs_area_active', table_name='hubs')
    op.drop_index('idx_hubs_active', table_name='hubs')
    op.drop_index('idx_hubs_zone', table_name='hubs')
    op.drop_index('idx_hubs_area', table_name='hubs')
    op.drop_index('idx_hubs_location', table_name='hubs')
    
    # Drop table
    op.drop_table('hubs')

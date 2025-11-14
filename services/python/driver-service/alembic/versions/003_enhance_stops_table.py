"""Alembic migration: Enhance stops table with hub association.

Revision ID: 003_enhance_stops_table
Revises: 002_create_hubs_table
Create Date: 2025-11-14

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = '003_enhance_stops_table'
down_revision: Union[str, None] = '002_create_hubs_table'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Add hub_id, area_id, and is_active columns to stops table."""
    # Add new columns
    op.add_column('stops', sa.Column('hub_id', postgresql.UUID(as_uuid=True), nullable=True))
    op.add_column('stops', sa.Column('area_id', sa.String(length=50), nullable=True))
    op.add_column('stops', sa.Column('is_active', sa.Boolean(), nullable=False, server_default=sa.text('true')))

    # Add foreign key constraint
    op.create_foreign_key(
        'fk_stops_hub',
        'stops', 'hubs',
        ['hub_id'], ['id'],
        ondelete='SET NULL'
    )

    # Create indexes
    op.create_index('idx_stops_hub_id', 'stops', ['hub_id'])
    op.create_index('idx_stops_area_id', 'stops', ['area_id'])
    op.create_index('idx_stops_is_active', 'stops', ['is_active'])
    op.create_index('idx_stops_hub_active', 'stops', ['hub_id', 'is_active'])

    # Create trigger function to auto-populate area_id from hub
    op.execute("""
        CREATE OR REPLACE FUNCTION update_stop_area_from_hub()
        RETURNS TRIGGER AS $$
        BEGIN
            IF NEW.hub_id IS NOT NULL THEN
                SELECT area_id INTO NEW.area_id
                FROM hubs
                WHERE id = NEW.hub_id;
            END IF;
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;
    """)

    # Create trigger
    op.execute("""
        CREATE TRIGGER trg_stops_area_from_hub
            BEFORE INSERT OR UPDATE OF hub_id ON stops
            FOR EACH ROW
            EXECUTE FUNCTION update_stop_area_from_hub();
    """)

    # Data migration: Assign each stop to nearest active hub within 1km
    op.execute("""
        UPDATE stops s
        SET hub_id = (
            SELECT h.id
            FROM hubs h
            WHERE h.is_active = true
            ORDER BY ST_Distance(
                ST_Transform(s.location::geometry, 4326)::geography,
                ST_Transform(h.location::geometry, 4326)::geography
            )
            LIMIT 1
        )
        WHERE s.hub_id IS NULL
          AND EXISTS (
              SELECT 1
              FROM hubs h
              WHERE h.is_active = true
                AND ST_DWithin(
                    ST_Transform(s.location::geometry, 4326)::geography,
                    ST_Transform(h.location::geometry, 4326)::geography,
                    1000
                )
          );
    """)

    # Update area_id for stops that got assigned to hubs
    op.execute("""
        UPDATE stops s
        SET area_id = h.area_id
        FROM hubs h
        WHERE s.hub_id = h.id AND s.area_id IS NULL;
    """)


def downgrade() -> None:
    """Remove hub association from stops table."""
    # Drop trigger
    op.execute('DROP TRIGGER IF EXISTS trg_stops_area_from_hub ON stops;')
    
    # Drop function
    op.execute('DROP FUNCTION IF EXISTS update_stop_area_from_hub();')
    
    # Drop indexes
    op.drop_index('idx_stops_hub_active', table_name='stops')
    op.drop_index('idx_stops_is_active', table_name='stops')
    op.drop_index('idx_stops_area_id', table_name='stops')
    op.drop_index('idx_stops_hub_id', table_name='stops')
    
    # Drop foreign key
    op.drop_constraint('fk_stops_hub', 'stops', type_='foreignkey')
    
    # Drop columns
    op.drop_column('stops', 'is_active')
    op.drop_column('stops', 'area_id')
    op.drop_column('stops', 'hub_id')

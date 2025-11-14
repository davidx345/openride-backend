"""Alembic migration: Add hub support to routes.

Revision ID: 004_add_hub_to_routes
Revises: 003_enhance_stops_table
Create Date: 2025-11-14

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = '004_add_hub_to_routes'
down_revision: Union[str, None] = '003_enhance_stops_table'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Add hub references and enhanced metadata to routes table."""
    # Add new columns
    op.add_column('routes', sa.Column('origin_hub_id', postgresql.UUID(as_uuid=True), nullable=True))
    op.add_column('routes', sa.Column('destination_hub_id', postgresql.UUID(as_uuid=True), nullable=True))
    op.add_column('routes', sa.Column('currency', sa.String(length=3), nullable=False, server_default='NGN'))
    op.add_column('routes', sa.Column('estimated_duration_minutes', sa.Integer(), nullable=True))
    op.add_column('routes', sa.Column('route_template_id', postgresql.UUID(as_uuid=True), nullable=True))

    # Add foreign key constraints
    op.create_foreign_key(
        'fk_routes_origin_hub',
        'routes', 'hubs',
        ['origin_hub_id'], ['id'],
        ondelete='SET NULL'
    )
    op.create_foreign_key(
        'fk_routes_destination_hub',
        'routes', 'hubs',
        ['destination_hub_id'], ['id'],
        ondelete='SET NULL'
    )

    # Create indexes
    op.create_index('idx_routes_origin_hub', 'routes', ['origin_hub_id'])
    op.create_index('idx_routes_destination_hub', 'routes', ['destination_hub_id'])
    op.create_index('idx_routes_hub_pair', 'routes', ['origin_hub_id', 'destination_hub_id'])
    op.create_index('idx_routes_currency', 'routes', ['currency'])
    op.create_index('idx_routes_template', 'routes', ['route_template_id'])

    # Data migration: Assign origin_hub_id from first stop
    op.execute("""
        UPDATE routes r
        SET origin_hub_id = (
            SELECT s.hub_id
            FROM route_stops rs
            JOIN stops s ON rs.stop_id = s.id
            WHERE rs.route_id = r.id
            ORDER BY rs.stop_order ASC
            LIMIT 1
        )
        WHERE r.origin_hub_id IS NULL;
    """)

    # Data migration: Assign destination_hub_id from last stop
    op.execute("""
        UPDATE routes r
        SET destination_hub_id = (
            SELECT s.hub_id
            FROM route_stops rs
            JOIN stops s ON rs.stop_id = s.id
            WHERE rs.route_id = r.id
            ORDER BY rs.stop_order DESC
            LIMIT 1
        )
        WHERE r.destination_hub_id IS NULL;
    """)

    # Data migration: Estimate duration from distance
    op.execute("""
        UPDATE routes
        SET estimated_duration_minutes = CASE
            WHEN start_location IS NOT NULL AND end_location IS NOT NULL THEN
                ROUND(
                    ST_Distance(
                        ST_Transform(start_location::geometry, 4326)::geography,
                        ST_Transform(end_location::geometry, 4326)::geography
                    ) / 1000.0 / 30.0 * 60.0
                )::INTEGER
            ELSE 30
            END
        WHERE estimated_duration_minutes IS NULL;
    """)


def downgrade() -> None:
    """Remove hub support from routes table."""
    # Drop indexes
    op.drop_index('idx_routes_template', table_name='routes')
    op.drop_index('idx_routes_currency', table_name='routes')
    op.drop_index('idx_routes_hub_pair', table_name='routes')
    op.drop_index('idx_routes_destination_hub', table_name='routes')
    op.drop_index('idx_routes_origin_hub', table_name='routes')
    
    # Drop foreign keys
    op.drop_constraint('fk_routes_destination_hub', 'routes', type_='foreignkey')
    op.drop_constraint('fk_routes_origin_hub', 'routes', type_='foreignkey')
    
    # Drop columns
    op.drop_column('routes', 'route_template_id')
    op.drop_column('routes', 'estimated_duration_minutes')
    op.drop_column('routes', 'currency')
    op.drop_column('routes', 'destination_hub_id')
    op.drop_column('routes', 'origin_hub_id')

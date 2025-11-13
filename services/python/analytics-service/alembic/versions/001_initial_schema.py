"""Initial schema for analytics service metadata tables.

Revision ID: 001
Revises: 
Create Date: 2024-11-14 10:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision: str = '001'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Create analytics metadata tables."""
    
    # ========================================
    # Report Configurations Table
    # ========================================
    op.create_table(
        'report_configs',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text('gen_random_uuid()')),
        sa.Column('name', sa.String(255), nullable=False),
        sa.Column('description', sa.Text, nullable=True),
        sa.Column('report_type', sa.String(50), nullable=False),  # 'user_metrics', 'booking_metrics', etc.
        sa.Column('schedule_cron', sa.String(100), nullable=True),  # Cron expression for scheduled reports
        sa.Column('query_template', sa.Text, nullable=False),  # SQL query template
        sa.Column('parameters', postgresql.JSONB, nullable=True),  # Default parameters
        sa.Column('output_format', sa.String(20), nullable=False, server_default='csv'),  # csv, excel, json
        sa.Column('recipients', postgresql.ARRAY(sa.String), nullable=True),  # Email recipients
        sa.Column('is_active', sa.Boolean, nullable=False, server_default='true'),
        sa.Column('created_by', postgresql.UUID(as_uuid=True), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('NOW()')),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('NOW()')),
        sa.CheckConstraint("report_type IN ('user_metrics', 'booking_metrics', 'payment_metrics', 'trip_metrics', 'driver_earnings', 'route_performance', 'custom')", name='ck_report_type'),
        sa.CheckConstraint("output_format IN ('csv', 'excel', 'json')", name='ck_output_format'),
    )
    op.create_index('idx_report_configs_type', 'report_configs', ['report_type'])
    op.create_index('idx_report_configs_active', 'report_configs', ['is_active'])
    
    # ========================================
    # Report Execution History Table
    # ========================================
    op.create_table(
        'report_executions',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text('gen_random_uuid()')),
        sa.Column('config_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('status', sa.String(20), nullable=False),  # pending, running, success, failed
        sa.Column('start_time', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('NOW()')),
        sa.Column('end_time', sa.DateTime(timezone=True), nullable=True),
        sa.Column('duration_seconds', sa.Integer, nullable=True),
        sa.Column('row_count', sa.Integer, nullable=True),
        sa.Column('file_path', sa.String(500), nullable=True),
        sa.Column('file_size_bytes', sa.BigInteger, nullable=True),
        sa.Column('error_message', sa.Text, nullable=True),
        sa.Column('parameters', postgresql.JSONB, nullable=True),
        sa.Column('triggered_by', sa.String(50), nullable=False),  # 'schedule', 'manual', 'api'
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('NOW()')),
        sa.ForeignKeyConstraint(['config_id'], ['report_configs.id'], ondelete='CASCADE'),
        sa.CheckConstraint("status IN ('pending', 'running', 'success', 'failed')", name='ck_execution_status'),
    )
    op.create_index('idx_report_executions_config', 'report_executions', ['config_id'])
    op.create_index('idx_report_executions_status', 'report_executions', ['status'])
    op.create_index('idx_report_executions_start_time', 'report_executions', ['start_time'])
    
    # ========================================
    # Scheduled Jobs Table (Celery Beat)
    # ========================================
    op.create_table(
        'scheduled_jobs',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text('gen_random_uuid()')),
        sa.Column('name', sa.String(255), nullable=False, unique=True),
        sa.Column('task_name', sa.String(255), nullable=False),  # Celery task name
        sa.Column('schedule_type', sa.String(20), nullable=False),  # 'cron', 'interval'
        sa.Column('schedule_config', postgresql.JSONB, nullable=False),  # Cron or interval config
        sa.Column('task_args', postgresql.JSONB, nullable=True),
        sa.Column('task_kwargs', postgresql.JSONB, nullable=True),
        sa.Column('is_enabled', sa.Boolean, nullable=False, server_default='true'),
        sa.Column('last_run_at', sa.DateTime(timezone=True), nullable=True),
        sa.Column('total_run_count', sa.Integer, nullable=False, server_default='0'),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('NOW()')),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('NOW()')),
        sa.CheckConstraint("schedule_type IN ('cron', 'interval')", name='ck_schedule_type'),
    )
    op.create_index('idx_scheduled_jobs_enabled', 'scheduled_jobs', ['is_enabled'])
    
    # ========================================
    # Metric Cache Table (Redis fallback)
    # ========================================
    op.create_table(
        'metric_cache',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text('gen_random_uuid()')),
        sa.Column('cache_key', sa.String(500), nullable=False, unique=True),
        sa.Column('metric_type', sa.String(100), nullable=False),
        sa.Column('metric_data', postgresql.JSONB, nullable=False),
        sa.Column('computed_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('NOW()')),
        sa.Column('expires_at', sa.DateTime(timezone=True), nullable=False),
        sa.Column('hit_count', sa.Integer, nullable=False, server_default='0'),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('NOW()')),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('NOW()')),
    )
    op.create_index('idx_metric_cache_key', 'metric_cache', ['cache_key'])
    op.create_index('idx_metric_cache_expires', 'metric_cache', ['expires_at'])
    
    # ========================================
    # Data Quality Checks Table
    # ========================================
    op.create_table(
        'data_quality_checks',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text('gen_random_uuid()')),
        sa.Column('check_name', sa.String(255), nullable=False),
        sa.Column('check_type', sa.String(50), nullable=False),  # 'completeness', 'consistency', 'accuracy'
        sa.Column('entity_type', sa.String(50), nullable=False),  # 'user', 'booking', 'payment', etc.
        sa.Column('check_query', sa.Text, nullable=False),
        sa.Column('threshold_value', sa.Float, nullable=True),
        sa.Column('threshold_operator', sa.String(10), nullable=True),  # '>', '<', '=', '>=', '<='
        sa.Column('is_critical', sa.Boolean, nullable=False, server_default='false'),
        sa.Column('is_enabled', sa.Boolean, nullable=False, server_default='true'),
        sa.Column('schedule_cron', sa.String(100), nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('NOW()')),
        sa.Column('updated_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('NOW()')),
    )
    op.create_index('idx_dq_checks_type', 'data_quality_checks', ['check_type'])
    op.create_index('idx_dq_checks_entity', 'data_quality_checks', ['entity_type'])
    
    # ========================================
    # Data Quality Results Table
    # ========================================
    op.create_table(
        'data_quality_results',
        sa.Column('id', postgresql.UUID(as_uuid=True), primary_key=True, server_default=sa.text('gen_random_uuid()')),
        sa.Column('check_id', postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column('check_timestamp', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('NOW()')),
        sa.Column('status', sa.String(20), nullable=False),  # 'passed', 'failed', 'warning'
        sa.Column('actual_value', sa.Float, nullable=True),
        sa.Column('expected_value', sa.Float, nullable=True),
        sa.Column('row_count', sa.Integer, nullable=True),
        sa.Column('error_details', postgresql.JSONB, nullable=True),
        sa.Column('created_at', sa.DateTime(timezone=True), nullable=False, server_default=sa.text('NOW()')),
        sa.ForeignKeyConstraint(['check_id'], ['data_quality_checks.id'], ondelete='CASCADE'),
        sa.CheckConstraint("status IN ('passed', 'failed', 'warning')", name='ck_dq_status'),
    )
    op.create_index('idx_dq_results_check', 'data_quality_results', ['check_id'])
    op.create_index('idx_dq_results_timestamp', 'data_quality_results', ['check_timestamp'])
    op.create_index('idx_dq_results_status', 'data_quality_results', ['status'])


def downgrade() -> None:
    """Drop all analytics metadata tables."""
    op.drop_table('data_quality_results')
    op.drop_table('data_quality_checks')
    op.drop_table('metric_cache')
    op.drop_table('scheduled_jobs')
    op.drop_table('report_executions')
    op.drop_table('report_configs')

"""SQLAlchemy database models for analytics service metadata."""

from datetime import datetime
from typing import Optional
from uuid import UUID, uuid4

from sqlalchemy import (
    BigInteger,
    Boolean,
    CheckConstraint,
    Column,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    String,
    Text,
)
from sqlalchemy.dialects.postgresql import ARRAY, JSONB, UUID as PG_UUID
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.sql import func

Base = declarative_base()


class ReportConfig(Base):
    """Report configuration for scheduled and on-demand reports."""

    __tablename__ = "report_configs"

    id = Column(PG_UUID(as_uuid=True), primary_key=True, default=uuid4)
    name = Column(String(255), nullable=False)
    description = Column(Text, nullable=True)
    report_type = Column(String(50), nullable=False)
    schedule_cron = Column(String(100), nullable=True)
    query_template = Column(Text, nullable=False)
    parameters = Column(JSONB, nullable=True)
    output_format = Column(String(20), nullable=False, server_default="csv")
    recipients = Column(ARRAY(String), nullable=True)
    is_active = Column(Boolean, nullable=False, server_default="true")
    created_by = Column(PG_UUID(as_uuid=True), nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    updated_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now(), onupdate=func.now())

    __table_args__ = (
        CheckConstraint(
            "report_type IN ('user_metrics', 'booking_metrics', 'payment_metrics', 'trip_metrics', 'driver_earnings', 'route_performance', 'custom')",
            name="ck_report_type",
        ),
        CheckConstraint(
            "output_format IN ('csv', 'excel', 'json')",
            name="ck_output_format",
        ),
    )


class ReportExecution(Base):
    """Report execution history and status."""

    __tablename__ = "report_executions"

    id = Column(PG_UUID(as_uuid=True), primary_key=True, default=uuid4)
    config_id = Column(PG_UUID(as_uuid=True), ForeignKey("report_configs.id", ondelete="CASCADE"), nullable=False)
    status = Column(String(20), nullable=False)
    start_time = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    end_time = Column(DateTime(timezone=True), nullable=True)
    duration_seconds = Column(Integer, nullable=True)
    row_count = Column(Integer, nullable=True)
    file_path = Column(String(500), nullable=True)
    file_size_bytes = Column(BigInteger, nullable=True)
    error_message = Column(Text, nullable=True)
    parameters = Column(JSONB, nullable=True)
    triggered_by = Column(String(50), nullable=False)
    created_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())

    __table_args__ = (
        CheckConstraint(
            "status IN ('pending', 'running', 'success', 'failed')",
            name="ck_execution_status",
        ),
    )


class ScheduledJob(Base):
    """Scheduled jobs for periodic tasks (Celery Beat)."""

    __tablename__ = "scheduled_jobs"

    id = Column(PG_UUID(as_uuid=True), primary_key=True, default=uuid4)
    name = Column(String(255), nullable=False, unique=True)
    task_name = Column(String(255), nullable=False)
    schedule_type = Column(String(20), nullable=False)
    schedule_config = Column(JSONB, nullable=False)
    task_args = Column(JSONB, nullable=True)
    task_kwargs = Column(JSONB, nullable=True)
    is_enabled = Column(Boolean, nullable=False, server_default="true")
    last_run_at = Column(DateTime(timezone=True), nullable=True)
    total_run_count = Column(Integer, nullable=False, server_default="0")
    created_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    updated_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now(), onupdate=func.now())

    __table_args__ = (
        CheckConstraint(
            "schedule_type IN ('cron', 'interval')",
            name="ck_schedule_type",
        ),
    )


class MetricCache(Base):
    """Metric cache for frequently accessed aggregations (Redis fallback)."""

    __tablename__ = "metric_cache"

    id = Column(PG_UUID(as_uuid=True), primary_key=True, default=uuid4)
    cache_key = Column(String(500), nullable=False, unique=True)
    metric_type = Column(String(100), nullable=False)
    metric_data = Column(JSONB, nullable=False)
    computed_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    expires_at = Column(DateTime(timezone=True), nullable=False)
    hit_count = Column(Integer, nullable=False, server_default="0")
    created_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    updated_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now(), onupdate=func.now())


class DataQualityCheck(Base):
    """Data quality check definitions."""

    __tablename__ = "data_quality_checks"

    id = Column(PG_UUID(as_uuid=True), primary_key=True, default=uuid4)
    check_name = Column(String(255), nullable=False)
    check_type = Column(String(50), nullable=False)
    entity_type = Column(String(50), nullable=False)
    check_query = Column(Text, nullable=False)
    threshold_value = Column(Float, nullable=True)
    threshold_operator = Column(String(10), nullable=True)
    is_critical = Column(Boolean, nullable=False, server_default="false")
    is_enabled = Column(Boolean, nullable=False, server_default="true")
    schedule_cron = Column(String(100), nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    updated_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now(), onupdate=func.now())


class DataQualityResult(Base):
    """Data quality check execution results."""

    __tablename__ = "data_quality_results"

    id = Column(PG_UUID(as_uuid=True), primary_key=True, default=uuid4)
    check_id = Column(PG_UUID(as_uuid=True), ForeignKey("data_quality_checks.id", ondelete="CASCADE"), nullable=False)
    check_timestamp = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    status = Column(String(20), nullable=False)
    actual_value = Column(Float, nullable=True)
    expected_value = Column(Float, nullable=True)
    row_count = Column(Integer, nullable=True)
    error_details = Column(JSONB, nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())

    __table_args__ = (
        CheckConstraint(
            "status IN ('passed', 'failed', 'warning')",
            name="ck_dq_status",
        ),
    )

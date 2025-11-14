"""Report scheduling schemas."""

from datetime import datetime
from enum import Enum
from typing import Dict, List, Optional
from uuid import UUID

from pydantic import BaseModel, EmailStr, Field


class ReportFrequency(str, Enum):
    """Report frequency."""
    
    DAILY = "daily"
    WEEKLY = "weekly"
    MONTHLY = "monthly"


class ReportType(str, Enum):
    """Report type."""
    
    USER_METRICS = "user_metrics"
    BOOKING_METRICS = "booking_metrics"
    PAYMENT_METRICS = "payment_metrics"
    TRIP_METRICS = "trip_metrics"
    DRIVER_EARNINGS = "driver_earnings"
    COMPREHENSIVE = "comprehensive"


class ReportFormat(str, Enum):
    """Report format."""
    
    PDF = "pdf"
    EXCEL = "excel"
    CSV = "csv"


class ReportScheduleCreate(BaseModel):
    """Create report schedule schema."""
    
    report_name: str = Field(..., min_length=1, max_length=255)
    report_type: ReportType
    frequency: ReportFrequency
    format: ReportFormat = ReportFormat.EXCEL
    recipients: List[EmailStr] = Field(..., min_items=1)
    parameters: Optional[Dict[str, any]] = Field(None, description="Report-specific parameters")
    active: bool = True
    description: Optional[str] = None


class ReportScheduleUpdate(BaseModel):
    """Update report schedule schema."""
    
    report_name: Optional[str] = None
    frequency: Optional[ReportFrequency] = None
    format: Optional[ReportFormat] = None
    recipients: Optional[List[EmailStr]] = None
    parameters: Optional[Dict[str, any]] = None
    active: Optional[bool] = None
    description: Optional[str] = None


class ReportSchedule(BaseModel):
    """Report schedule model."""
    
    id: UUID
    report_name: str
    report_type: ReportType
    frequency: ReportFrequency
    format: ReportFormat
    recipients: List[str]
    parameters: Optional[Dict[str, any]]
    active: bool
    description: Optional[str]
    created_at: datetime
    updated_at: datetime
    last_execution: Optional[datetime]
    next_execution: Optional[datetime]


class ReportScheduleResponse(ReportSchedule):
    """Report schedule response."""
    
    pass


class ExecutionStatus(str, Enum):
    """Report execution status."""
    
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"


class ScheduledReportExecution(BaseModel):
    """Scheduled report execution record."""
    
    id: UUID
    schedule_id: UUID
    status: ExecutionStatus
    started_at: datetime
    completed_at: Optional[datetime]
    file_path: Optional[str]
    error_message: Optional[str]
    rows_exported: Optional[int]

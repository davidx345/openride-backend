"""Export schemas."""

from datetime import datetime
from enum import Enum
from typing import Dict, Optional

from pydantic import BaseModel, Field


class ExportFormat(str, Enum):
    """Export file format."""
    
    CSV = "csv"
    EXCEL = "excel"


class ExportStatus(str, Enum):
    """Export status."""
    
    PENDING = "pending"
    PROCESSING = "processing"
    COMPLETED = "completed"
    FAILED = "failed"


class ExportRequest(BaseModel):
    """Export request schema."""
    
    query: str = Field(..., description="ClickHouse SQL query to export")
    format: ExportFormat = Field(ExportFormat.CSV, description="Export format")
    filename: Optional[str] = Field(None, description="Custom filename (without extension)")
    query_params: Optional[Dict[str, any]] = Field(None, description="Query parameters")
    row_limit: int = Field(100000, ge=1, le=1000000, description="Maximum rows to export")


class ExportResponse(BaseModel):
    """Export response schema."""
    
    export_id: str
    status: ExportStatus
    message: Optional[str] = None
    file_path: Optional[str] = None
    row_count: Optional[int] = None
    error: Optional[str] = None
    created_at: datetime
    updated_at: Optional[datetime] = None

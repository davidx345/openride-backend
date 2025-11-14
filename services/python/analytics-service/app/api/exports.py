"""Data export API endpoints."""

import asyncio
import csv
import os
import uuid
from datetime import datetime
from io import StringIO
from typing import Dict, List, Optional

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Query
from fastapi.responses import FileResponse, StreamingResponse
from clickhouse_connect.driver import Client
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill

from app.core import get_clickhouse, settings
from app.core.logging import get_logger
from app.schemas.exports import (
    ExportFormat,
    ExportRequest,
    ExportResponse,
    ExportStatus,
)
from app.services.exports import ExportService

logger = get_logger(__name__)

router = APIRouter()


def get_export_service(ch: Client = Depends(get_clickhouse)) -> ExportService:
    """Dependency to get export service."""
    return ExportService(ch)


@router.post("/exports/request", response_model=ExportResponse)
async def create_export_request(
    request: ExportRequest,
    background_tasks: BackgroundTasks,
    service: ExportService = Depends(get_export_service)
):
    """
    Create a data export request.
    
    Exports are processed asynchronously. Use the export_id to check status.
    """
    try:
        export_id = str(uuid.uuid4())
        
        # Validate request
        if request.row_limit > settings.EXPORT_MAX_ROWS:
            raise HTTPException(
                status_code=400,
                detail=f"Row limit exceeds maximum of {settings.EXPORT_MAX_ROWS}"
            )
        
        # Start export in background
        background_tasks.add_task(
            service.process_export,
            export_id=export_id,
            request=request
        )
        
        return ExportResponse(
            export_id=export_id,
            status=ExportStatus.PENDING,
            message="Export request submitted. Processing in background.",
            created_at=datetime.utcnow(),
        )
        
    except Exception as e:
        logger.error("create_export_request_failed", error=str(e))
        raise HTTPException(status_code=500, detail="Failed to create export request")


@router.get("/exports/{export_id}/status", response_model=ExportResponse)
async def get_export_status(
    export_id: str,
    service: ExportService = Depends(get_export_service)
):
    """Get status of an export request."""
    status = await service.get_export_status(export_id)
    if not status:
        raise HTTPException(status_code=404, detail="Export not found")
    return status


@router.get("/exports/{export_id}/download")
async def download_export(
    export_id: str,
    service: ExportService = Depends(get_export_service)
):
    """Download completed export file."""
    try:
        file_path = await service.get_export_file(export_id)
        if not file_path:
            raise HTTPException(status_code=404, detail="Export file not found")
        
        if not os.path.exists(file_path):
            raise HTTPException(status_code=404, detail="Export file no longer available")
        
        # Determine media type
        media_type = "text/csv" if file_path.endswith(".csv") else \
                     "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        
        return FileResponse(
            path=file_path,
            media_type=media_type,
            filename=os.path.basename(file_path)
        )
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error("download_export_failed", export_id=export_id, error=str(e))
        raise HTTPException(status_code=500, detail="Failed to download export")


@router.get("/exports/quick/csv")
async def quick_export_csv(
    query: str = Query(..., description="ClickHouse SQL query"),
    filename: str = Query("export.csv", description="Output filename"),
    service: ExportService = Depends(get_export_service)
):
    """
    Quick CSV export (synchronous, max 10,000 rows).
    
    For larger exports, use /exports/request endpoint.
    """
    try:
        # Limit query to prevent abuse
        limited_query = f"{query} LIMIT 10000"
        
        # Execute query
        result = await service.execute_query(limited_query)
        
        # Generate CSV
        output = StringIO()
        writer = csv.writer(output)
        
        # Write header
        if result.column_names:
            writer.writerow(result.column_names)
        
        # Write data
        for row in result.result_rows:
            writer.writerow(row)
        
        output.seek(0)
        
        return StreamingResponse(
            iter([output.getvalue()]),
            media_type="text/csv",
            headers={"Content-Disposition": f"attachment; filename={filename}"}
        )
        
    except Exception as e:
        logger.error("quick_export_csv_failed", error=str(e))
        raise HTTPException(status_code=500, detail="Failed to export CSV")


@router.get("/exports/metrics/excel")
async def export_metrics_excel(
    start_date: datetime = Query(..., description="Start date (ISO 8601)"),
    end_date: datetime = Query(..., description="End date (ISO 8601)"),
    service: ExportService = Depends(get_export_service)
):
    """
    Export comprehensive metrics report as Excel workbook.
    
    Includes multiple sheets: Users, Bookings, Payments, Trips, Drivers, Routes.
    """
    try:
        filename = f"openride_metrics_{start_date.date()}_{end_date.date()}.xlsx"
        file_path = os.path.join(settings.EXPORT_TEMP_DIR, filename)
        
        # Create export directory if not exists
        os.makedirs(settings.EXPORT_TEMP_DIR, exist_ok=True)
        
        # Generate Excel workbook
        await service.generate_metrics_excel(start_date, end_date, file_path)
        
        return FileResponse(
            path=file_path,
            media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            filename=filename
        )
        
    except Exception as e:
        logger.error("export_metrics_excel_failed", error=str(e))
        raise HTTPException(status_code=500, detail="Failed to export metrics")


@router.delete("/exports/{export_id}")
async def delete_export(
    export_id: str,
    service: ExportService = Depends(get_export_service)
):
    """Delete an export file and its metadata."""
    try:
        success = await service.delete_export(export_id)
        if not success:
            raise HTTPException(status_code=404, detail="Export not found")
        
        return {"message": "Export deleted successfully"}
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error("delete_export_failed", export_id=export_id, error=str(e))
        raise HTTPException(status_code=500, detail="Failed to delete export")

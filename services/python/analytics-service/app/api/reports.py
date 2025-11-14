"""Report scheduling API endpoints."""

from datetime import datetime
from typing import List, Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.logging import get_logger
from app.schemas.reports import (
    ReportSchedule,
    ReportScheduleCreate,
    ReportScheduleResponse,
    ReportScheduleUpdate,
    ScheduledReportExecution,
)
from app.services.reports import ReportSchedulingService

logger = get_logger(__name__)

router = APIRouter()


def get_report_service(db: AsyncSession = Depends(get_db)) -> ReportSchedulingService:
    """Dependency to get report scheduling service."""
    return ReportSchedulingService(db)


@router.post("/reports/schedule", response_model=ReportScheduleResponse, status_code=status.HTTP_201_CREATED)
async def create_report_schedule(
    schedule: ReportScheduleCreate,
    service: ReportSchedulingService = Depends(get_report_service)
):
    """
    Create a new scheduled report.
    
    Supports daily, weekly, and monthly report generation with email delivery.
    """
    try:
        created_schedule = await service.create_schedule(schedule)
        return created_schedule
        
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.error("create_report_schedule_failed", error=str(e))
        raise HTTPException(status_code=500, detail="Failed to create report schedule")


@router.get("/reports/schedule", response_model=List[ReportScheduleResponse])
async def list_report_schedules(
    active_only: bool = Query(True, description="Filter by active schedules only"),
    service: ReportSchedulingService = Depends(get_report_service)
):
    """List all scheduled reports."""
    try:
        schedules = await service.list_schedules(active_only=active_only)
        return schedules
        
    except Exception as e:
        logger.error("list_report_schedules_failed", error=str(e))
        raise HTTPException(status_code=500, detail="Failed to list report schedules")


@router.get("/reports/schedule/{schedule_id}", response_model=ReportScheduleResponse)
async def get_report_schedule(
    schedule_id: UUID,
    service: ReportSchedulingService = Depends(get_report_service)
):
    """Get a specific scheduled report by ID."""
    try:
        schedule = await service.get_schedule(schedule_id)
        if not schedule:
            raise HTTPException(status_code=404, detail="Report schedule not found")
        return schedule
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error("get_report_schedule_failed", schedule_id=str(schedule_id), error=str(e))
        raise HTTPException(status_code=500, detail="Failed to get report schedule")


@router.patch("/reports/schedule/{schedule_id}", response_model=ReportScheduleResponse)
async def update_report_schedule(
    schedule_id: UUID,
    update: ReportScheduleUpdate,
    service: ReportSchedulingService = Depends(get_report_service)
):
    """Update a scheduled report."""
    try:
        updated_schedule = await service.update_schedule(schedule_id, update)
        if not updated_schedule:
            raise HTTPException(status_code=404, detail="Report schedule not found")
        return updated_schedule
        
    except HTTPException:
        raise
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.error("update_report_schedule_failed", schedule_id=str(schedule_id), error=str(e))
        raise HTTPException(status_code=500, detail="Failed to update report schedule")


@router.delete("/reports/schedule/{schedule_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_report_schedule(
    schedule_id: UUID,
    service: ReportSchedulingService = Depends(get_report_service)
):
    """Delete a scheduled report."""
    try:
        success = await service.delete_schedule(schedule_id)
        if not success:
            raise HTTPException(status_code=404, detail="Report schedule not found")
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error("delete_report_schedule_failed", schedule_id=str(schedule_id), error=str(e))
        raise HTTPException(status_code=500, detail="Failed to delete report schedule")


@router.post("/reports/schedule/{schedule_id}/execute", response_model=dict)
async def trigger_report_execution(
    schedule_id: UUID,
    service: ReportSchedulingService = Depends(get_report_service)
):
    """Manually trigger execution of a scheduled report."""
    try:
        execution_id = await service.trigger_execution(schedule_id)
        return {
            "message": "Report execution triggered",
            "execution_id": str(execution_id),
            "schedule_id": str(schedule_id)
        }
        
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.error("trigger_report_execution_failed", schedule_id=str(schedule_id), error=str(e))
        raise HTTPException(status_code=500, detail="Failed to trigger report execution")


@router.get("/reports/schedule/{schedule_id}/executions", response_model=List[ScheduledReportExecution])
async def list_report_executions(
    schedule_id: UUID,
    limit: int = Query(50, ge=1, le=100),
    service: ReportSchedulingService = Depends(get_report_service)
):
    """Get execution history for a scheduled report."""
    try:
        executions = await service.list_executions(schedule_id, limit=limit)
        return executions
        
    except Exception as e:
        logger.error("list_report_executions_failed", schedule_id=str(schedule_id), error=str(e))
        raise HTTPException(status_code=500, detail="Failed to list report executions")


@router.get("/reports/executions/{execution_id}", response_model=ScheduledReportExecution)
async def get_report_execution(
    execution_id: UUID,
    service: ReportSchedulingService = Depends(get_report_service)
):
    """Get details of a specific report execution."""
    try:
        execution = await service.get_execution(execution_id)
        if not execution:
            raise HTTPException(status_code=404, detail="Report execution not found")
        return execution
        
    except HTTPException:
        raise
    except Exception as e:
        logger.error("get_report_execution_failed", execution_id=str(execution_id), error=str(e))
        raise HTTPException(status_code=500, detail="Failed to get report execution")

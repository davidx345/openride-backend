"""Report scheduling service for automated report generation."""

import uuid
from datetime import datetime, timedelta
from typing import List, Optional
from uuid import UUID

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.logging import get_logger
from app.db.models import ReportScheduleModel, ReportExecutionModel
from app.schemas.reports import (
    ExecutionStatus,
    ReportScheduleCreate,
    ReportScheduleResponse,
    ReportScheduleUpdate,
    ScheduledReportExecution,
)

logger = get_logger(__name__)


class ReportSchedulingService:
    """Service for managing scheduled reports."""

    def __init__(self, db: AsyncSession):
        """Initialize service with database session."""
        self.db = db

    async def create_schedule(self, schedule: ReportScheduleCreate) -> ReportScheduleResponse:
        """Create a new report schedule."""
        try:
            # Calculate next execution time
            next_execution = self._calculate_next_execution(schedule.frequency)
            
            # Create model
            model = ReportScheduleModel(
                id=uuid.uuid4(),
                report_name=schedule.report_name,
                report_type=schedule.report_type,
                frequency=schedule.frequency,
                format=schedule.format,
                recipients=schedule.recipients,
                parameters=schedule.parameters or {},
                active=schedule.active,
                description=schedule.description,
                next_execution=next_execution,
            )
            
            self.db.add(model)
            await self.db.commit()
            await self.db.refresh(model)
            
            return ReportScheduleResponse.model_validate(model)
            
        except Exception as e:
            await self.db.rollback()
            logger.error("create_schedule_failed", error=str(e))
            raise

    async def list_schedules(self, active_only: bool = True) -> List[ReportScheduleResponse]:
        """List all report schedules."""
        try:
            query = select(ReportScheduleModel)
            if active_only:
                query = query.where(ReportScheduleModel.active == True)
            
            result = await self.db.execute(query)
            schedules = result.scalars().all()
            
            return [ReportScheduleResponse.model_validate(s) for s in schedules]
            
        except Exception as e:
            logger.error("list_schedules_failed", error=str(e))
            raise

    async def get_schedule(self, schedule_id: UUID) -> Optional[ReportScheduleResponse]:
        """Get a specific schedule by ID."""
        try:
            result = await self.db.execute(
                select(ReportScheduleModel).where(ReportScheduleModel.id == schedule_id)
            )
            schedule = result.scalar_one_or_none()
            
            if not schedule:
                return None
            
            return ReportScheduleResponse.model_validate(schedule)
            
        except Exception as e:
            logger.error("get_schedule_failed", schedule_id=str(schedule_id), error=str(e))
            raise

    async def update_schedule(
        self,
        schedule_id: UUID,
        update: ReportScheduleUpdate
    ) -> Optional[ReportScheduleResponse]:
        """Update a report schedule."""
        try:
            result = await self.db.execute(
                select(ReportScheduleModel).where(ReportScheduleModel.id == schedule_id)
            )
            schedule = result.scalar_one_or_none()
            
            if not schedule:
                return None
            
            # Update fields
            update_data = update.model_dump(exclude_unset=True)
            for field, value in update_data.items():
                setattr(schedule, field, value)
            
            # Recalculate next execution if frequency changed
            if update.frequency:
                schedule.next_execution = self._calculate_next_execution(update.frequency)
            
            schedule.updated_at = datetime.utcnow()
            
            await self.db.commit()
            await self.db.refresh(schedule)
            
            return ReportScheduleResponse.model_validate(schedule)
            
        except Exception as e:
            await self.db.rollback()
            logger.error("update_schedule_failed", schedule_id=str(schedule_id), error=str(e))
            raise

    async def delete_schedule(self, schedule_id: UUID) -> bool:
        """Delete a report schedule."""
        try:
            result = await self.db.execute(
                select(ReportScheduleModel).where(ReportScheduleModel.id == schedule_id)
            )
            schedule = result.scalar_one_or_none()
            
            if not schedule:
                return False
            
            await self.db.delete(schedule)
            await self.db.commit()
            
            return True
            
        except Exception as e:
            await self.db.rollback()
            logger.error("delete_schedule_failed", schedule_id=str(schedule_id), error=str(e))
            raise

    async def trigger_execution(self, schedule_id: UUID) -> UUID:
        """Manually trigger report execution."""
        try:
            # Verify schedule exists
            schedule = await self.get_schedule(schedule_id)
            if not schedule:
                raise ValueError(f"Schedule {schedule_id} not found")
            
            # Create execution record
            execution = ReportExecutionModel(
                id=uuid.uuid4(),
                schedule_id=schedule_id,
                status=ExecutionStatus.PENDING,
                started_at=datetime.utcnow(),
            )
            
            self.db.add(execution)
            await self.db.commit()
            
            # TODO: Trigger Celery task for report generation
            
            return execution.id
            
        except Exception as e:
            await self.db.rollback()
            logger.error("trigger_execution_failed", schedule_id=str(schedule_id), error=str(e))
            raise

    async def list_executions(
        self,
        schedule_id: UUID,
        limit: int = 50
    ) -> List[ScheduledReportExecution]:
        """List execution history for a schedule."""
        try:
            result = await self.db.execute(
                select(ReportExecutionModel)
                .where(ReportExecutionModel.schedule_id == schedule_id)
                .order_by(ReportExecutionModel.started_at.desc())
                .limit(limit)
            )
            executions = result.scalars().all()
            
            return [ScheduledReportExecution.model_validate(e) for e in executions]
            
        except Exception as e:
            logger.error("list_executions_failed", schedule_id=str(schedule_id), error=str(e))
            raise

    async def get_execution(self, execution_id: UUID) -> Optional[ScheduledReportExecution]:
        """Get a specific execution record."""
        try:
            result = await self.db.execute(
                select(ReportExecutionModel).where(ReportExecutionModel.id == execution_id)
            )
            execution = result.scalar_one_or_none()
            
            if not execution:
                return None
            
            return ScheduledReportExecution.model_validate(execution)
            
        except Exception as e:
            logger.error("get_execution_failed", execution_id=str(execution_id), error=str(e))
            raise

    def _calculate_next_execution(self, frequency: str) -> datetime:
        """Calculate next execution time based on frequency."""
        now = datetime.utcnow()
        
        if frequency == "daily":
            # Next day at 6 AM UTC
            next_exec = now.replace(hour=6, minute=0, second=0, microsecond=0)
            if next_exec <= now:
                next_exec += timedelta(days=1)
        elif frequency == "weekly":
            # Next Monday at 6 AM UTC
            days_ahead = 0 - now.weekday()
            if days_ahead <= 0:
                days_ahead += 7
            next_exec = now + timedelta(days=days_ahead)
            next_exec = next_exec.replace(hour=6, minute=0, second=0, microsecond=0)
        else:  # monthly
            # First day of next month at 6 AM UTC
            if now.month == 12:
                next_exec = now.replace(year=now.year + 1, month=1, day=1, hour=6, minute=0, second=0, microsecond=0)
            else:
                next_exec = now.replace(month=now.month + 1, day=1, hour=6, minute=0, second=0, microsecond=0)
        
        return next_exec

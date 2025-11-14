"""Admin notification template management API endpoints."""
import logging
from typing import List
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from app.database import get_db_session
from app.models import NotificationTemplate
from app.schemas import (
    NotificationTemplateCreate,
    NotificationTemplateUpdate,
    NotificationTemplateResponse,
)

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/v1/admin/notification-templates", tags=["admin-templates"])


@router.post("", response_model=NotificationTemplateResponse, status_code=status.HTTP_201_CREATED)
async def create_template(
    request: NotificationTemplateCreate,
    db: AsyncSession = Depends(get_db_session),
    # TODO: Add admin role check
) -> NotificationTemplateResponse:
    """
    Create a new notification template.
    """
    try:
        # Check if template key already exists
        result = await db.execute(
            select(NotificationTemplate).where(
                NotificationTemplate.template_key == request.template_key
            )
        )
        existing = result.scalar_one_or_none()

        if existing:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"Template with key '{request.template_key}' already exists",
            )

        template = NotificationTemplate(
            template_key=request.template_key,
            notification_type=request.notification_type,
            channel=request.channel,
            subject_template=request.subject_template,
            body_template=request.body_template,
            is_active=True,
        )

        db.add(template)
        await db.commit()
        await db.refresh(template)

        logger.info(f"Created template: {template.template_key}")
        return NotificationTemplateResponse.model_validate(template)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to create template: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to create template: {str(e)}",
        )


@router.get("", response_model=List[NotificationTemplateResponse])
async def list_templates(
    is_active: bool = Query(None, description="Filter by active status"),
    db: AsyncSession = Depends(get_db_session),
    # TODO: Add admin role check
) -> List[NotificationTemplateResponse]:
    """
    List all notification templates.
    """
    try:
        query = select(NotificationTemplate)

        if is_active is not None:
            query = query.where(NotificationTemplate.is_active == is_active)

        result = await db.execute(query)
        templates = result.scalars().all()

        return [NotificationTemplateResponse.model_validate(t) for t in templates]
    except Exception as e:
        logger.error(f"Failed to list templates: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to list templates: {str(e)}",
        )


@router.get("/{template_id}", response_model=NotificationTemplateResponse)
async def get_template(
    template_id: UUID,
    db: AsyncSession = Depends(get_db_session),
    # TODO: Add admin role check
) -> NotificationTemplateResponse:
    """
    Get a specific template by ID.
    """
    try:
        result = await db.execute(
            select(NotificationTemplate).where(NotificationTemplate.id == template_id)
        )
        template = result.scalar_one_or_none()

        if not template:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Template not found",
            )

        return NotificationTemplateResponse.model_validate(template)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to get template: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to get template: {str(e)}",
        )


@router.put("/{template_id}", response_model=NotificationTemplateResponse)
async def update_template(
    template_id: UUID,
    request: NotificationTemplateUpdate,
    db: AsyncSession = Depends(get_db_session),
    # TODO: Add admin role check
) -> NotificationTemplateResponse:
    """
    Update a notification template.
    """
    try:
        result = await db.execute(
            select(NotificationTemplate).where(NotificationTemplate.id == template_id)
        )
        template = result.scalar_one_or_none()

        if not template:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Template not found",
            )

        # Update fields
        update_data = request.model_dump(exclude_unset=True)
        for field, value in update_data.items():
            setattr(template, field, value)

        await db.commit()
        await db.refresh(template)

        logger.info(f"Updated template: {template.template_key}")
        return NotificationTemplateResponse.model_validate(template)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to update template: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to update template: {str(e)}",
        )


@router.delete("/{template_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_template(
    template_id: UUID,
    db: AsyncSession = Depends(get_db_session),
    # TODO: Add admin role check
) -> None:
    """
    Delete (deactivate) a notification template.
    """
    try:
        result = await db.execute(
            select(NotificationTemplate).where(NotificationTemplate.id == template_id)
        )
        template = result.scalar_one_or_none()

        if not template:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Template not found",
            )

        template.is_active = False
        await db.commit()

        logger.info(f"Deleted template: {template.template_key}")
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to delete template: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to delete template: {str(e)}",
        )

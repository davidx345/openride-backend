"""Notification API endpoints."""
import logging
from typing import List
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.ext.asyncio import AsyncSession
from app.database import get_db_session
from app.auth import get_current_user
from app.schemas import (
    SendNotificationRequest,
    NotificationResponse,
    NotificationLogResponse,
    BroadcastRequest,
)
from app.services.notification_service import NotificationService
from app.tasks.notification_tasks import send_notification_async, send_broadcast_async

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/v1/notifications", tags=["notifications"])


@router.post("/send", response_model=NotificationResponse, status_code=status.HTTP_200_OK)
async def send_notification(
    request: SendNotificationRequest,
    db: AsyncSession = Depends(get_db_session),
    current_user: UUID = Depends(get_current_user),
) -> NotificationResponse:
    """
    Send a notification to a user.

    This endpoint sends a notification immediately (synchronous).
    For async processing, use the /send-async endpoint.
    """
    notification_service = NotificationService()

    try:
        # Extract user contact info from request
        user_phone = request.phone_number
        user_email = request.email
        user_name = request.template_data.get("user_name") if request.template_data else None

        # Send through each channel
        results = []
        for channel in request.channels:
            result = await notification_service.send_notification(
                db=db,
                user_id=request.user_id,
                notification_type=request.notification_type,
                channel=channel,
                template_data=request.template_data,
                user_phone=user_phone,
                user_email=user_email,
                user_name=user_name,
            )
            results.append(result)

        # Return first successful or last failed
        final_result = next((r for r in results if r.get("success")), results[-1])
        
        return NotificationResponse(
            notification_id=UUID(final_result.get("notification_id", "00000000-0000-0000-0000-000000000000")),
            status=NotificationStatus.SENT if final_result.get("success") else NotificationStatus.FAILED,
            message=final_result.get("message", ""),
        )
    except Exception as e:
        logger.error(f"Failed to send notification: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to send notification: {str(e)}",
        )


@router.post("/send-async", status_code=status.HTTP_202_ACCEPTED)
async def send_notification_async_endpoint(
    request: SendNotificationRequest,
) -> dict:
    """
    Send a notification asynchronously via Celery.

    Returns immediately with task ID.
    """
    try:
        # Extract user contact info from request data
        user_phone = request.data.get("user_phone") if request.data else None
        user_email = request.data.get("user_email") if request.data else None
        user_name = request.data.get("user_name") if request.data else None

        task = send_notification_async.delay(
            user_id=str(request.user_id),
            notification_type=request.notification_type.value,
            channel=request.channel.value,
            data=request.data or {},
            user_phone=user_phone,
            user_email=user_email,
            user_name=user_name,
        )

        return {
            "success": True,
            "message": "Notification queued for processing",
            "task_id": task.id,
        }
    except Exception as e:
        logger.error(f"Failed to queue notification: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to queue notification: {str(e)}",
        )


@router.post("/broadcast", status_code=status.HTTP_202_ACCEPTED)
async def broadcast_notification(
    request: BroadcastRequest,
) -> dict:
    """
    Send notification to multiple users asynchronously.

    Returns immediately with task ID.
    """
    try:
        # Convert UUIDs to strings for Celery
        user_ids = [str(uid) for uid in request.user_ids]
        user_data = {str(k): v for k, v in request.user_data.items()}

        task = send_broadcast_async.delay(
            user_ids=user_ids,
            notification_type=request.notification_type.value,
            channel=request.channel.value,
            data=request.data or {},
            user_data=user_data,
        )

        return {
            "success": True,
            "message": f"Broadcast queued for {len(user_ids)} users",
            "task_id": task.id,
        }
    except Exception as e:
        logger.error(f"Failed to queue broadcast: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to queue broadcast: {str(e)}",
        )


@router.get("/history", response_model=List[NotificationLogResponse])
async def get_notification_history(
    user_id: UUID = Query(..., description="User ID"),
    limit: int = Query(50, ge=1, le=100, description="Number of records"),
    offset: int = Query(0, ge=0, description="Number of records to skip"),
    db: AsyncSession = Depends(get_db_session),
    current_user: UUID = Depends(get_current_user),
) -> List[NotificationLogResponse]:
    """
    Get notification history for a user.
    """
    notification_service = NotificationService()

    try:
        history = await notification_service.get_notification_history(
            db=db,
            user_id=user_id,
            limit=limit,
            offset=offset,
        )
        return history
    except Exception as e:
        logger.error(f"Failed to get notification history: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to get notification history: {str(e)}",
        )


@router.get("/{notification_id}", response_model=NotificationLogResponse)
async def get_notification(
    notification_id: UUID,
    db: AsyncSession = Depends(get_db_session),
    current_user: UUID = Depends(get_current_user),
) -> NotificationLogResponse:
    """
    Get details of a specific notification.
    """
    from sqlalchemy import select
    from app.models import NotificationLog

    try:
        result = await db.execute(
            select(NotificationLog).where(NotificationLog.id == notification_id)
        )
        notification = result.scalar_one_or_none()

        if not notification:
            raise HTTPException(
                status_code=status.HTTP_404_NOT_FOUND,
                detail="Notification not found",
            )

        return NotificationLogResponse.model_validate(notification)
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Failed to get notification: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to get notification: {str(e)}",
        )

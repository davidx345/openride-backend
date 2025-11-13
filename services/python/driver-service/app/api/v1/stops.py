"""
Stop API endpoints.
"""
import logging
from typing import List
from uuid import UUID
from decimal import Decimal

from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.exceptions import StopNotFoundException
from app.schemas.stop import StopResponse
from app.services.stop_service import StopService

router = APIRouter()
logger = logging.getLogger(__name__)


@router.get("", response_model=List[StopResponse])
async def search_stops(
    lat: Decimal = Query(..., ge=-90, le=90, description="Latitude"),
    lon: Decimal = Query(..., ge=-180, le=180, description="Longitude"),
    radius_km: float = Query(default=5.0, ge=0.1, le=50.0, description="Search radius in kilometers"),
    limit: int = Query(default=20, ge=1, le=100, description="Maximum number of results"),
    db: AsyncSession = Depends(get_db)
):
    """
    Search stops by proximity.
    
    Public endpoint - no authentication required.
    """
    try:
        service = StopService(db)
        stops = await service.search_stops(float(lat), float(lon), radius_km, limit)
        return stops
    except Exception as e:
        logger.error(f"Error searching stops: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to search stops"
        )


@router.get("/{stop_id}", response_model=StopResponse)
async def get_stop(
    stop_id: UUID,
    db: AsyncSession = Depends(get_db)
):
    """
    Get stop by ID.
    
    Public endpoint - no authentication required.
    """
    try:
        service = StopService(db)
        stop = await service.get_stop(stop_id)
        return stop
    except StopNotFoundException as e:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(e))
    except Exception as e:
        logger.error(f"Error getting stop: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to get stop"
        )

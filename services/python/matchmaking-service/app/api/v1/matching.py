"""Matching API endpoints."""

import logging
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db
from app.core.exceptions import MatchingError
from app.schemas.matching import MatchRequest, MatchResponse
from app.services.matching_service import MatchingService

router = APIRouter(prefix="/match", tags=["matching"])
logger = logging.getLogger(__name__)


@router.post(
    "",
    response_model=MatchResponse,
    status_code=status.HTTP_200_OK,
    summary="Match riders to routes",
    description="""
    Intelligent route matching endpoint.
    
    Finds and ranks routes based on:
    - Geospatial proximity to origin/destination
    - Departure time compatibility
    - Driver ratings
    - Price
    
    Returns ranked list of matching routes with scores and explanations.
    """,
)
async def match_routes(
    request: MatchRequest,
    db: Annotated[AsyncSession, Depends(get_db)],
) -> MatchResponse:
    """
    Match riders to compatible routes with intelligent ranking.

    Args:
        request: Match request with rider preferences
        db: Database session

    Returns:
        MatchResponse: Ranked matches with scores

    Raises:
        HTTPException: If matching fails
    """
    try:
        logger.info(
            f"Match request from rider {request.rider_id}: "
            f"origin=({request.origin_lat}, {request.origin_lon}), "
            f"time={request.desired_time}"
        )

        service = MatchingService(db)
        response = await service.match_routes(request)

        logger.info(
            f"Match completed: {len(response.matches)} matches, "
            f"{response.execution_time_ms}ms execution time"
        )

        return response

    except MatchingError as e:
        logger.error(f"Matching error: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e),
        )
    except Exception as e:
        logger.error(f"Unexpected error in matching: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Internal server error during matching",
        )

"""Health check and monitoring endpoints"""
from datetime import datetime

from fastapi import APIRouter, Depends, status
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.redis import get_redis
from app.db import get_db

router = APIRouter(tags=["health"])


@router.get("/health", status_code=status.HTTP_200_OK)
async def health_check(
    db: AsyncSession = Depends(get_db),
    redis = Depends(get_redis),
):
    """Health check endpoint for monitoring
    
    Checks:
    - Service is running
    - Database connection is healthy
    - Redis connection is healthy
    """
    health_status = {
        "status": "healthy",
        "service": settings.SERVICE_NAME,
        "timestamp": datetime.utcnow().isoformat(),
        "checks": {}
    }
    
    # Check database
    try:
        await db.execute(text("SELECT 1"))
        health_status["checks"]["database"] = "healthy"
    except Exception as e:
        health_status["status"] = "unhealthy"
        health_status["checks"]["database"] = f"unhealthy: {str(e)}"
    
    # Check Redis
    try:
        await redis.ping()
        health_status["checks"]["redis"] = "healthy"
    except Exception as e:
        health_status["status"] = "unhealthy"
        health_status["checks"]["redis"] = f"unhealthy: {str(e)}"
    
    return health_status


@router.get("/ready", status_code=status.HTTP_200_OK)
async def readiness_check(
    db: AsyncSession = Depends(get_db),
    redis = Depends(get_redis),
):
    """Readiness check for Kubernetes/load balancers"""
    try:
        # Check database
        await db.execute(text("SELECT 1"))
        
        # Check Redis
        await redis.ping()
        
        return {"status": "ready"}
    except Exception as e:
        return {"status": "not ready", "error": str(e)}


@router.get("/live", status_code=status.HTTP_200_OK)
async def liveness_check():
    """Liveness check for Kubernetes"""
    return {"status": "alive"}

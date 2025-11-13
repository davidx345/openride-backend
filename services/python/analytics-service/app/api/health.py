"""Health check API endpoints."""

from fastapi import APIRouter, Depends, status
from clickhouse_connect.driver import Client
from redis.asyncio import Redis

from app.core import get_clickhouse, get_redis
from app.core.logging import get_logger

logger = get_logger(__name__)

router = APIRouter()


@router.get("/health", status_code=status.HTTP_200_OK)
async def health_check():
    """Basic health check endpoint."""
    return {
        "status": "healthy",
        "service": "analytics-service",
    }


@router.get("/health/ready", status_code=status.HTTP_200_OK)
async def readiness_check(
    redis: Redis = Depends(get_redis),
    clickhouse: Client = Depends(get_clickhouse)
):
    """Readiness check with dependency validation."""
    checks = {
        "redis": "unknown",
        "clickhouse": "unknown",
    }
    
    # Check Redis
    try:
        await redis.ping()
        checks["redis"] = "healthy"
    except Exception as e:
        logger.error("redis_health_check_failed", error=str(e))
        checks["redis"] = "unhealthy"
    
    # Check ClickHouse
    try:
        clickhouse.ping()
        checks["clickhouse"] = "healthy"
    except Exception as e:
        logger.error("clickhouse_health_check_failed", error=str(e))
        checks["clickhouse"] = "unhealthy"
    
    all_healthy = all(v == "healthy" for v in checks.values())
    
    return {
        "status": "healthy" if all_healthy else "degraded",
        "checks": checks,
    }


@router.get("/health/live", status_code=status.HTTP_200_OK)
async def liveness_check():
    """Liveness probe endpoint."""
    return {"status": "alive"}

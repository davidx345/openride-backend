"""Performance monitoring middleware for request timing."""

import logging
import time
from typing import Callable

from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware

from app.core.config import get_settings
from app.core.database import get_pool_status

settings = get_settings()
logger = logging.getLogger(__name__)


class PerformanceMiddleware(BaseHTTPMiddleware):
    """Middleware to track request performance and log slow operations."""

    async def dispatch(self, request: Request, call_next: Callable) -> Response:
        """
        Track request timing and log performance metrics.

        Args:
            request: Incoming request
            call_next: Next middleware/handler

        Returns:
            Response: HTTP response
        """
        start_time = time.time()
        
        # Add timing context to request state
        request.state.start_time = start_time
        request.state.timings = {}
        
        # Process request
        response = await call_next(request)
        
        # Calculate total time
        total_time_ms = (time.time() - start_time) * 1000
        
        # Add timing headers
        response.headers["X-Response-Time"] = f"{total_time_ms:.2f}ms"
        
        # Log request details
        log_data = {
            "method": request.method,
            "path": request.url.path,
            "status_code": response.status_code,
            "duration_ms": round(total_time_ms, 2),
        }
        
        # Add breakdown if available
        if hasattr(request.state, "timings") and request.state.timings:
            log_data["breakdown"] = request.state.timings
        
        # Check against performance target
        if total_time_ms > settings.performance_target_ms:
            logger.warning(
                f"Slow request detected: {log_data}",
                extra={
                    "performance_violation": True,
                    "target_ms": settings.performance_target_ms,
                    "actual_ms": total_time_ms,
                    "overage_ms": total_time_ms - settings.performance_target_ms,
                }
            )
        else:
            logger.info(f"Request completed: {log_data}")
        
        # Log pool status if monitoring enabled
        if settings.enable_pool_monitoring:
            pool_status = get_pool_status()
            logger.debug(f"Connection pool status: {pool_status}")
        
        return response


def track_operation(request: Request, operation_name: str, duration_ms: float):
    """
    Track timing for a specific operation within a request.

    Args:
        request: Current request
        operation_name: Name of the operation
        duration_ms: Duration in milliseconds
    """
    if hasattr(request.state, "timings"):
        request.state.timings[operation_name] = round(duration_ms, 2)
        
        # Log slow operations
        if (
            settings.enable_query_logging
            and duration_ms > settings.slow_query_threshold_ms
        ):
            logger.warning(
                f"Slow operation: {operation_name} took {duration_ms:.2f}ms "
                f"(threshold: {settings.slow_query_threshold_ms}ms)"
            )

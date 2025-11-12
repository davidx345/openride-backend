"""
Middleware for OpenRide Python services.
"""

import time
import uuid
from typing import Callable
from fastapi import Request, Response
from starlette.middleware.base import BaseHTTPMiddleware
from openride_commons.logging import set_correlation_id, clear_correlation_id
import logging

logger = logging.getLogger(__name__)


class CorrelationIdMiddleware(BaseHTTPMiddleware):
    """
    Middleware that adds correlation ID to each request for distributed tracing.
    The correlation ID is added to response headers and logging context.
    """

    CORRELATION_ID_HEADER = "X-Correlation-ID"

    async def dispatch(
        self, request: Request, call_next: Callable
    ) -> Response:
        # Get correlation ID from header or generate new one
        correlation_id = request.headers.get(self.CORRELATION_ID_HEADER)
        if not correlation_id:
            correlation_id = str(uuid.uuid4())

        # Set correlation ID in logging context
        set_correlation_id(correlation_id)

        try:
            # Process request
            response = await call_next(request)

            # Add correlation ID to response headers
            response.headers[self.CORRELATION_ID_HEADER] = correlation_id

            return response
        finally:
            # Clean up logging context
            clear_correlation_id()


class RequestLoggingMiddleware(BaseHTTPMiddleware):
    """
    Middleware that logs request and response details.
    """

    async def dispatch(
        self, request: Request, call_next: Callable
    ) -> Response:
        start_time = time.time()

        # Log request
        logger.info(
            f"Request: {request.method} {request.url.path} "
            f"from {request.client.host if request.client else 'unknown'}"
        )

        # Process request
        response = await call_next(request)

        # Calculate request duration
        duration = time.time() - start_time

        # Log response
        logger.info(
            f"Response: {response.status_code} "
            f"Duration: {duration:.3f}s"
        )

        # Add duration header
        response.headers["X-Response-Time"] = f"{duration:.3f}"

        return response

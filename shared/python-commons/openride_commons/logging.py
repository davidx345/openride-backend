"""
Logging utilities for OpenRide Python services.
"""

import logging
import sys
import uuid
from contextvars import ContextVar
from typing import Optional

# Context variable for correlation ID
correlation_id_var: ContextVar[Optional[str]] = ContextVar(
    "correlation_id", default=None
)


class CorrelationIdFilter(logging.Filter):
    """
    Logging filter that adds correlation ID to log records.
    """

    def filter(self, record: logging.LogRecord) -> bool:
        """Add correlation ID to the log record."""
        record.correlation_id = correlation_id_var.get() or "N/A"
        return True


def setup_logging(
    service_name: str,
    log_level: str = "INFO",
    log_format: Optional[str] = None,
) -> None:
    """
    Set up logging configuration for a service.

    Args:
        service_name: Name of the service
        log_level: Logging level (DEBUG, INFO, WARNING, ERROR, CRITICAL)
        log_format: Custom log format string
    """
    if log_format is None:
        log_format = (
            "%(asctime)s - %(name)s - %(levelname)s - "
            "[%(correlation_id)s] - %(message)s"
        )

    # Configure root logger
    logging.basicConfig(
        level=getattr(logging, log_level.upper()),
        format=log_format,
        handlers=[logging.StreamHandler(sys.stdout)],
    )

    # Add correlation ID filter to all handlers
    for handler in logging.root.handlers:
        handler.addFilter(CorrelationIdFilter())

    # Set service name in logger
    logger = logging.getLogger(service_name)
    logger.info(f"{service_name} logging initialized at {log_level} level")


def get_correlation_id() -> str:
    """
    Get the current correlation ID or generate a new one.

    Returns:
        Correlation ID
    """
    correlation_id = correlation_id_var.get()
    if correlation_id is None:
        correlation_id = str(uuid.uuid4())
        correlation_id_var.set(correlation_id)
    return correlation_id


def set_correlation_id(correlation_id: str) -> None:
    """
    Set the correlation ID for the current context.

    Args:
        correlation_id: The correlation ID to set
    """
    correlation_id_var.set(correlation_id)


def clear_correlation_id() -> None:
    """Clear the correlation ID from the current context."""
    correlation_id_var.set(None)

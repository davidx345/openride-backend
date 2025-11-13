"""Custom exception classes."""

from typing import Any


class MatchmakingException(Exception):
    """Base exception for matchmaking service."""

    def __init__(self, message: str, details: dict[str, Any] | None = None):
        self.message = message
        self.details = details or {}
        super().__init__(self.message)


class ValidationError(MatchmakingException):
    """Validation error."""

    pass


class NotFoundError(MatchmakingException):
    """Resource not found error."""

    pass


class ExternalServiceError(MatchmakingException):
    """External service communication error."""

    pass


class CacheError(MatchmakingException):
    """Redis cache operation error."""

    pass


class MatchingError(MatchmakingException):
    """Route matching algorithm error."""

    pass


class PerformanceError(MatchmakingException):
    """Performance target exceeded error."""

    pass

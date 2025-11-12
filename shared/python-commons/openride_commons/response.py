"""
Standard response models for OpenRide Python services.
"""

from datetime import datetime
from typing import Any, Generic, Optional, TypeVar
from pydantic import BaseModel, Field

T = TypeVar("T")


class MetaData(BaseModel):
    """Metadata for API responses."""

    timestamp: datetime = Field(default_factory=datetime.utcnow)
    correlation_id: Optional[str] = None
    page: Optional[int] = None
    page_size: Optional[int] = None
    total_elements: Optional[int] = None


class ErrorDetails(BaseModel):
    """Error details for failed responses."""

    code: str
    message: str
    details: Optional[dict] = None


class ApiResponse(BaseModel, Generic[T]):
    """
    Standard API response wrapper for all OpenRide endpoints.
    Provides consistent response structure across all services.
    """

    success: bool
    data: Optional[T] = None
    error: Optional[ErrorDetails] = None
    meta: MetaData = Field(default_factory=MetaData)


class SuccessResponse(BaseModel, Generic[T]):
    """Helper class for creating success responses."""

    @staticmethod
    def create(data: T = None, meta: MetaData = None) -> ApiResponse[T]:
        """
        Creates a successful response with data.

        Args:
            data: The response data
            meta: Optional metadata

        Returns:
            ApiResponse with success=true
        """
        return ApiResponse(
            success=True,
            data=data,
            meta=meta or MetaData()
        )


class ErrorResponse(BaseModel):
    """Helper class for creating error responses."""

    @staticmethod
    def create(
        error_code: str,
        message: str,
        details: dict = None,
        meta: MetaData = None
    ) -> ApiResponse[None]:
        """
        Creates an error response.

        Args:
            error_code: The error code
            message: The error message
            details: Optional additional error details
            meta: Optional metadata

        Returns:
            ApiResponse with success=false
        """
        return ApiResponse(
            success=False,
            error=ErrorDetails(
                code=error_code,
                message=message,
                details=details
            ),
            meta=meta or MetaData()
        )


class PaginatedData(BaseModel, Generic[T]):
    """Container for paginated data."""

    items: list[T]
    page: int
    page_size: int
    total_elements: int
    total_pages: int

    @property
    def has_next(self) -> bool:
        """Check if there are more pages."""
        return self.page < self.total_pages

    @property
    def has_prev(self) -> bool:
        """Check if there are previous pages."""
        return self.page > 1

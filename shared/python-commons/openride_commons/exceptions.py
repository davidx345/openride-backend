"""
Exception classes for OpenRide Python services.
"""


class BusinessException(Exception):
    """
    Base exception for business logic errors.
    Use this for expected errors that represent business rule violations.
    """

    def __init__(self, error_code: str, message: str, details: dict = None):
        """
        Creates a new business exception.

        Args:
            error_code: The error code for this business exception
            message: The error message
            details: Optional additional details about the error
        """
        self.error_code = error_code
        self.message = message
        self.details = details or {}
        super().__init__(self.message)


class TechnicalException(Exception):
    """
    Base exception for technical/system errors.
    Use this for unexpected errors like database failures, network issues, etc.
    """

    def __init__(self, error_code: str, message: str, cause: Exception = None):
        """
        Creates a new technical exception.

        Args:
            error_code: The error code for this technical exception
            message: The error message
            cause: The underlying cause exception
        """
        self.error_code = error_code
        self.message = message
        self.cause = cause
        super().__init__(self.message)


class NotFoundException(BusinessException):
    """Exception raised when a requested resource is not found."""

    def __init__(self, resource: str, identifier: str):
        super().__init__(
            error_code="NOT_FOUND",
            message=f"{resource} with identifier '{identifier}' not found",
            details={"resource": resource, "identifier": identifier},
        )


class UnauthorizedException(BusinessException):
    """Exception raised for unauthorized access attempts."""

    def __init__(self, message: str = "Unauthorized"):
        super().__init__(error_code="UNAUTHORIZED", message=message)


class ForbiddenException(BusinessException):
    """Exception raised when access to a resource is forbidden."""

    def __init__(self, message: str = "Forbidden"):
        super().__init__(error_code="FORBIDDEN", message=message)


class ValidationException(BusinessException):
    """Exception raised for validation errors."""

    def __init__(self, message: str, field: str = None, details: dict = None):
        error_details = details or {}
        if field:
            error_details["field"] = field
        super().__init__(error_code="VALIDATION_ERROR", message=message, details=error_details)

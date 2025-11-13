"""
Custom exceptions for the Driver Service.
"""


class DriverServiceException(Exception):
    """Base exception for Driver Service."""
    pass


class VehicleNotFoundException(DriverServiceException):
    """Raised when vehicle is not found."""
    pass


class RouteNotFoundException(DriverServiceException):
    """Raised when route is not found."""
    pass


class StopNotFoundException(DriverServiceException):
    """Raised when stop is not found."""
    pass


class UnauthorizedException(DriverServiceException):
    """Raised when user is not authorized to perform action."""
    pass


class ValidationException(DriverServiceException):
    """Raised when validation fails."""
    pass


class KYCNotVerifiedException(DriverServiceException):
    """Raised when driver KYC is not verified."""
    pass


class RateLimitExceededException(DriverServiceException):
    """Raised when rate limit is exceeded."""
    pass

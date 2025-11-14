"""JWT authentication utilities."""
import logging
from datetime import datetime, timedelta
from typing import Optional, Dict, Any
from uuid import UUID
from jose import JWTError, jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from app.config import settings

logger = logging.getLogger(__name__)

# Security scheme
security = HTTPBearer()


class JWTAuth:
    """JWT authentication handler."""

    @staticmethod
    def create_access_token(
        user_id: UUID,
        additional_claims: Optional[Dict[str, Any]] = None,
    ) -> str:
        """
        Create JWT access token.

        Args:
            user_id: User UUID
            additional_claims: Additional JWT claims

        Returns:
            Encoded JWT token
        """
        payload = {
            "sub": str(user_id),
            "iat": datetime.utcnow(),
            "exp": datetime.utcnow() + timedelta(minutes=settings.jwt_expiration_minutes),
        }

        if additional_claims:
            payload.update(additional_claims)

        return jwt.encode(payload, settings.jwt_secret_key, algorithm=settings.jwt_algorithm)

    @staticmethod
    def decode_token(token: str) -> Dict[str, Any]:
        """
        Decode and validate JWT token.

        Args:
            token: JWT token string

        Returns:
            Decoded token payload

        Raises:
            HTTPException: If token is invalid or expired
        """
        try:
            payload = jwt.decode(
                token,
                settings.jwt_secret_key,
                algorithms=[settings.jwt_algorithm],
            )
            return payload
        except JWTError as e:
            logger.error(f"JWT decode error: {e}")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid or expired token",
                headers={"WWW-Authenticate": "Bearer"},
            )


async def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security),
) -> UUID:
    """
    Get current user from JWT token.

    Args:
        credentials: HTTP authorization credentials

    Returns:
        User UUID

    Raises:
        HTTPException: If authentication fails
    """
    token = credentials.credentials

    try:
        payload = JWTAuth.decode_token(token)
        user_id = payload.get("sub")

        if user_id is None:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid token payload",
                headers={"WWW-Authenticate": "Bearer"},
            )

        return UUID(user_id)
    except ValueError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid user ID in token",
            headers={"WWW-Authenticate": "Bearer"},
        )


async def get_current_user_optional(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(HTTPBearer(auto_error=False)),
) -> Optional[UUID]:
    """
    Get current user from JWT token (optional).

    Args:
        credentials: HTTP authorization credentials (optional)

    Returns:
        User UUID or None if no credentials provided
    """
    if credentials is None:
        return None

    try:
        return await get_current_user(credentials)
    except HTTPException:
        return None


async def require_admin(
    current_user: UUID = Depends(get_current_user),
    credentials: HTTPAuthorizationCredentials = Depends(security),
) -> UUID:
    """
    Require admin role for endpoint access.

    Args:
        current_user: Current user UUID
        credentials: HTTP authorization credentials

    Returns:
        User UUID

    Raises:
        HTTPException: If user is not an admin
    """
    token = credentials.credentials
    payload = JWTAuth.decode_token(token)

    # Check for admin role in token claims
    roles = payload.get("roles", [])
    if "admin" not in roles:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Admin access required",
        )

    return current_user

"""
Security utilities for JWT authentication and authorization.
"""
from datetime import datetime
from typing import Optional
from uuid import UUID

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from jose import JWTError, jwt

from app.core.config import settings

security = HTTPBearer()


class TokenData:
    """JWT token data model."""
    
    def __init__(self, user_id: UUID, role: str, phone: str):
        self.user_id = user_id
        self.role = role
        self.phone = phone


async def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security)
) -> TokenData:
    """
    Validate JWT token and extract user data.
    
    Args:
        credentials: HTTP authorization credentials
        
    Returns:
        TokenData: Decoded token data
        
    Raises:
        HTTPException: If token is invalid or expired
    """
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Could not validate credentials",
        headers={"WWW-Authenticate": "Bearer"},
    )
    
    try:
        token = credentials.credentials
        payload = jwt.decode(
            token,
            settings.JWT_SECRET_KEY,
            algorithms=[settings.JWT_ALGORITHM]
        )
        
        user_id: Optional[str] = payload.get("sub")
        role: Optional[str] = payload.get("role")
        phone: Optional[str] = payload.get("phone")
        
        if user_id is None or role is None:
            raise credentials_exception
            
        # Check expiration
        exp = payload.get("exp")
        if exp is None or datetime.utcnow().timestamp() > exp:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Token has expired"
            )
        
        return TokenData(user_id=UUID(user_id), role=role, phone=phone)
        
    except JWTError:
        raise credentials_exception


async def require_driver_role(
    current_user: TokenData = Depends(get_current_user)
) -> TokenData:
    """
    Ensure current user has DRIVER role.
    
    Args:
        current_user: Current authenticated user
        
    Returns:
        TokenData: Current user data
        
    Raises:
        HTTPException: If user is not a driver
    """
    if current_user.role not in ["DRIVER", "ADMIN"]:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only drivers can access this resource"
        )
    return current_user

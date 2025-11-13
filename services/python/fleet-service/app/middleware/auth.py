"""JWT authentication middleware for REST API"""
from typing import Optional
from uuid import UUID

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.services.auth import AuthenticationError, verify_jwt_token

security = HTTPBearer()


async def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security),
) -> dict:
    """Get current authenticated user from JWT token
    
    Args:
        credentials: HTTP Bearer credentials
        
    Returns:
        User payload dictionary
        
    Raises:
        HTTPException: If authentication fails
    """
    try:
        payload = verify_jwt_token(credentials.credentials)
        return payload
    except AuthenticationError as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=str(e),
            headers={"WWW-Authenticate": "Bearer"},
        )


async def get_current_driver(
    current_user: dict = Depends(get_current_user),
) -> UUID:
    """Get current driver ID from authenticated user
    
    Requires user to have DRIVER role
    
    Args:
        current_user: Current user payload
        
    Returns:
        Driver UUID
        
    Raises:
        HTTPException: If user is not a driver
    """
    if current_user.get('role') != 'DRIVER':
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Driver role required"
        )
    
    return UUID(current_user['sub'])


async def get_current_rider(
    current_user: dict = Depends(get_current_user),
) -> UUID:
    """Get current rider ID from authenticated user
    
    Requires user to have RIDER role
    
    Args:
        current_user: Current user payload
        
    Returns:
        Rider UUID
        
    Raises:
        HTTPException: If user is not a rider
    """
    if current_user.get('role') != 'RIDER':
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Rider role required"
        )
    
    return UUID(current_user['sub'])


async def get_current_user_id(
    current_user: dict = Depends(get_current_user),
) -> UUID:
    """Get current user ID from authenticated user (any role)
    
    Args:
        current_user: Current user payload
        
    Returns:
        User UUID
    """
    return UUID(current_user['sub'])

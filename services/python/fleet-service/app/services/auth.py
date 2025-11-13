"""JWT authentication utilities"""
from datetime import datetime, timedelta
from typing import Dict, Any

from jose import JWTError, jwt

from app.core.config import settings
from app.core.logging import get_logger

logger = get_logger(__name__)


class AuthenticationError(Exception):
    """Authentication failed exception"""
    pass


def verify_jwt_token(token: str) -> Dict[str, Any]:
    """Verify and decode JWT token
    
    Args:
        token: JWT token string
        
    Returns:
        Decoded payload dictionary
        
    Raises:
        AuthenticationError: If token is invalid
    """
    try:
        payload = jwt.decode(
            token,
            settings.JWT_SECRET_KEY,
            algorithms=[settings.JWT_ALGORITHM],
        )
        
        # Check expiration
        exp = payload.get('exp')
        if exp and datetime.fromtimestamp(exp) < datetime.utcnow():
            raise AuthenticationError("Token expired")
        
        # Validate required claims
        if 'sub' not in payload:
            raise AuthenticationError("Invalid token: missing subject")
        
        return payload
        
    except JWTError as e:
        logger.warning(f"JWT verification failed: {e}")
        raise AuthenticationError(f"Invalid token: {e}")


def create_jwt_token(user_id: str, role: str, expires_delta: timedelta = None) -> str:
    """Create a new JWT token
    
    Args:
        user_id: User identifier
        role: User role (DRIVER or RIDER)
        expires_delta: Token expiration time delta
        
    Returns:
        Encoded JWT token string
    """
    if expires_delta is None:
        expires_delta = timedelta(hours=24)
    
    expire = datetime.utcnow() + expires_delta
    
    payload = {
        'sub': user_id,
        'role': role,
        'exp': expire,
        'iat': datetime.utcnow(),
    }
    
    token = jwt.encode(
        payload,
        settings.JWT_SECRET_KEY,
        algorithm=settings.JWT_ALGORITHM,
    )
    
    return token

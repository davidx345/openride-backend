"""Tests for JWT authentication."""
import pytest
from datetime import datetime, timedelta
from uuid import uuid4
from jose import jwt
from fastapi import HTTPException
from app.auth import JWTAuth, get_current_user, require_admin
from app.config import settings


class TestJWTAuth:
    """Test JWT authentication."""

    def test_create_access_token(self):
        """Test creating JWT access token."""
        user_id = uuid4()
        token = JWTAuth.create_access_token(user_id)

        assert token is not None
        assert isinstance(token, str)

        # Decode and verify
        payload = jwt.decode(token, settings.jwt_secret_key, algorithms=[settings.jwt_algorithm])
        assert payload["sub"] == str(user_id)
        assert "iat" in payload
        assert "exp" in payload

    def test_create_access_token_with_claims(self):
        """Test creating token with additional claims."""
        user_id = uuid4()
        additional_claims = {"roles": ["admin", "user"], "email": "test@example.com"}
        token = JWTAuth.create_access_token(user_id, additional_claims)

        payload = jwt.decode(token, settings.jwt_secret_key, algorithms=[settings.jwt_algorithm])
        assert payload["sub"] == str(user_id)
        assert payload["roles"] == ["admin", "user"]
        assert payload["email"] == "test@example.com"

    def test_decode_token_success(self):
        """Test decoding valid token."""
        user_id = uuid4()
        token = JWTAuth.create_access_token(user_id)

        payload = JWTAuth.decode_token(token)
        assert payload["sub"] == str(user_id)

    def test_decode_token_invalid(self):
        """Test decoding invalid token."""
        with pytest.raises(HTTPException) as exc_info:
            JWTAuth.decode_token("invalid_token")

        assert exc_info.value.status_code == 401
        assert "Invalid or expired token" in str(exc_info.value.detail)

    def test_decode_token_expired(self):
        """Test decoding expired token."""
        user_id = uuid4()
        
        # Create expired token
        payload = {
            "sub": str(user_id),
            "iat": datetime.utcnow() - timedelta(hours=2),
            "exp": datetime.utcnow() - timedelta(hours=1),
        }
        expired_token = jwt.encode(payload, settings.jwt_secret_key, algorithm=settings.jwt_algorithm)

        with pytest.raises(HTTPException) as exc_info:
            JWTAuth.decode_token(expired_token)

        assert exc_info.value.status_code == 401

    @pytest.mark.asyncio
    async def test_get_current_user_success(self, mocker):
        """Test getting current user from valid token."""
        user_id = uuid4()
        token = JWTAuth.create_access_token(user_id)

        # Mock HTTPAuthorizationCredentials
        mock_credentials = mocker.Mock()
        mock_credentials.credentials = token

        result = await get_current_user(mock_credentials)
        assert result == user_id

    @pytest.mark.asyncio
    async def test_get_current_user_invalid_token(self, mocker):
        """Test getting current user with invalid token."""
        mock_credentials = mocker.Mock()
        mock_credentials.credentials = "invalid_token"

        with pytest.raises(HTTPException) as exc_info:
            await get_current_user(mock_credentials)

        assert exc_info.value.status_code == 401

    @pytest.mark.asyncio
    async def test_require_admin_success(self, mocker):
        """Test admin requirement with admin role."""
        user_id = uuid4()
        token = JWTAuth.create_access_token(user_id, {"roles": ["admin", "user"]})

        mock_credentials = mocker.Mock()
        mock_credentials.credentials = token

        result = await require_admin(user_id, mock_credentials)
        assert result == user_id

    @pytest.mark.asyncio
    async def test_require_admin_no_role(self, mocker):
        """Test admin requirement without admin role."""
        user_id = uuid4()
        token = JWTAuth.create_access_token(user_id, {"roles": ["user"]})

        mock_credentials = mocker.Mock()
        mock_credentials.credentials = token

        with pytest.raises(HTTPException) as exc_info:
            await require_admin(user_id, mock_credentials)

        assert exc_info.value.status_code == 403
        assert "Admin access required" in str(exc_info.value.detail)

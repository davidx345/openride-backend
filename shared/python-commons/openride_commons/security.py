"""
JWT security utilities for OpenRide Python services.
"""

from datetime import datetime, timedelta
from typing import Optional
from jose import JWTError, jwt
from passlib.context import CryptContext


class JwtUtil:
    """Utility class for JWT token generation and validation."""

    def __init__(
        self,
        secret_key: str,
        algorithm: str = "HS256",
        access_token_expire_minutes: int = 60,
        refresh_token_expire_days: int = 7,
    ):
        """
        Initialize JWT utility.

        Args:
            secret_key: Secret key for signing tokens
            algorithm: JWT signing algorithm
            access_token_expire_minutes: Expiration time for access tokens in minutes
            refresh_token_expire_days: Expiration time for refresh tokens in days
        """
        self.secret_key = secret_key
        self.algorithm = algorithm
        self.access_token_expire_minutes = access_token_expire_minutes
        self.refresh_token_expire_days = refresh_token_expire_days

    def generate_access_token(
        self, user_id: str, phone: str, role: str
    ) -> str:
        """
        Generate an access token for a user.

        Args:
            user_id: The user ID
            phone: The user's phone number
            role: The user's role

        Returns:
            JWT access token
        """
        now = datetime.utcnow()
        expires_delta = timedelta(minutes=self.access_token_expire_minutes)
        expire = now + expires_delta

        to_encode = {
            "sub": user_id,
            "phone": phone,
            "role": role,
            "iat": now,
            "exp": expire,
        }

        encoded_jwt = jwt.encode(
            to_encode, self.secret_key, algorithm=self.algorithm
        )
        return encoded_jwt

    def generate_refresh_token(self, user_id: str) -> str:
        """
        Generate a refresh token for a user.

        Args:
            user_id: The user ID

        Returns:
            JWT refresh token
        """
        now = datetime.utcnow()
        expires_delta = timedelta(days=self.refresh_token_expire_days)
        expire = now + expires_delta

        to_encode = {
            "sub": user_id,
            "iat": now,
            "exp": expire,
        }

        encoded_jwt = jwt.encode(
            to_encode, self.secret_key, algorithm=self.algorithm
        )
        return encoded_jwt

    def decode_token(self, token: str) -> dict:
        """
        Decode and validate a JWT token.

        Args:
            token: The JWT token

        Returns:
            Decoded token payload

        Raises:
            JWTError: If token is invalid or expired
        """
        try:
            payload = jwt.decode(
                token, self.secret_key, algorithms=[self.algorithm]
            )
            return payload
        except JWTError as e:
            raise JWTError(f"Invalid token: {str(e)}")

    def extract_user_id(self, token: str) -> Optional[str]:
        """
        Extract user ID from a token.

        Args:
            token: The JWT token

        Returns:
            User ID or None if invalid
        """
        try:
            payload = self.decode_token(token)
            return payload.get("sub")
        except JWTError:
            return None

    def extract_role(self, token: str) -> Optional[str]:
        """
        Extract role from a token.

        Args:
            token: The JWT token

        Returns:
            User role or None if invalid
        """
        try:
            payload = self.decode_token(token)
            return payload.get("role")
        except JWTError:
            return None

    def validate_token(self, token: str) -> bool:
        """
        Validate a JWT token.

        Args:
            token: The JWT token

        Returns:
            True if valid, False otherwise
        """
        try:
            self.decode_token(token)
            return True
        except JWTError:
            return False


class PasswordUtil:
    """Utility class for password hashing and verification."""

    def __init__(self):
        self.pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

    def hash_password(self, password: str) -> str:
        """
        Hash a password.

        Args:
            password: Plain text password

        Returns:
            Hashed password
        """
        return self.pwd_context.hash(password)

    def verify_password(self, plain_password: str, hashed_password: str) -> bool:
        """
        Verify a password against a hash.

        Args:
            plain_password: Plain text password
            hashed_password: Hashed password

        Returns:
            True if password matches, False otherwise
        """
        return self.pwd_context.verify(plain_password, hashed_password)

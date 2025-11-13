"""Application configuration and settings"""
import os
from typing import List

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Application settings from environment variables"""
    
    # Service
    SERVICE_NAME: str = "fleet-service"
    LOG_LEVEL: str = "INFO"
    PORT: int = 8096
    
    # Database
    DATABASE_URL: str = "postgresql+asyncpg://openride_user:openride_password@localhost:5432/openride"
    DB_POOL_SIZE: int = 20
    DB_MAX_OVERFLOW: int = 10
    
    # Redis
    REDIS_URL: str = "redis://localhost:6379/0"
    REDIS_PASSWORD: str = ""
    REDIS_MAX_CONNECTIONS: int = 50
    
    # JWT
    JWT_SECRET_KEY: str = "your-secret-key-change-in-production"
    JWT_ALGORITHM: str = "HS256"
    
    # WebSocket
    SOCKETIO_PATH: str = "/socket.io"
    SOCKETIO_CORS_ALLOWED_ORIGINS: str = "*"
    
    # Rate Limiting
    LOCATION_UPDATE_RATE_LIMIT: int = 5  # seconds between updates
    MAX_CONNECTIONS_PER_DRIVER: int = 3
    
    # Performance
    WORKER_CONNECTIONS: int = 1000
    
    # Monitoring
    ENABLE_METRICS: bool = True
    METRICS_PORT: int = 9096
    
    @property
    def cors_origins(self) -> List[str]:
        """Parse CORS origins from comma-separated string"""
        if self.SOCKETIO_CORS_ALLOWED_ORIGINS == "*":
            return ["*"]
        return [
            origin.strip()
            for origin in self.SOCKETIO_CORS_ALLOWED_ORIGINS.split(",")
            if origin.strip()
        ]
    
    @property
    def redis_url_with_password(self) -> str:
        """Get Redis URL with password if configured"""
        if self.REDIS_PASSWORD:
            # Insert password into URL
            url = self.REDIS_URL.replace("redis://", f"redis://:{self.REDIS_PASSWORD}@")
            return url
        return self.REDIS_URL
    
    class Config:
        env_file = ".env"
        case_sensitive = True


# Global settings instance
settings = Settings()

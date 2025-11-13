"""
Application configuration using Pydantic settings.
"""
from typing import List
from pydantic import Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""
    
    # Application
    APP_NAME: str = "driver-service"
    ENVIRONMENT: str = Field(default="development", env="ENVIRONMENT")
    DEBUG: bool = Field(default=False, env="DEBUG")
    
    # Database
    DATABASE_URL: str = Field(
        default="postgresql+asyncpg://openride:openride@localhost:5432/openride",
        env="DATABASE_URL"
    )
    DATABASE_POOL_SIZE: int = Field(default=10, env="DATABASE_POOL_SIZE")
    DATABASE_MAX_OVERFLOW: int = Field(default=20, env="DATABASE_MAX_OVERFLOW")
    
    # Redis
    REDIS_URL: str = Field(default="redis://localhost:6379/0", env="REDIS_URL")
    REDIS_CACHE_TTL: int = Field(default=300, env="REDIS_CACHE_TTL")
    
    # JWT
    JWT_SECRET_KEY: str = Field(default="changeme", env="JWT_SECRET_KEY")
    JWT_ALGORITHM: str = Field(default="HS256", env="JWT_ALGORITHM")
    
    # CORS
    CORS_ORIGINS: List[str] = Field(
        default=["http://localhost:3000", "http://localhost:8000"],
        env="CORS_ORIGINS"
    )
    
    # Rate Limiting
    RATE_LIMIT_ROUTES_PER_DAY: int = Field(default=10, env="RATE_LIMIT_ROUTES_PER_DAY")
    
    # Geospatial
    STOP_DEDUP_RADIUS_METERS: float = Field(default=10.0, env="STOP_DEDUP_RADIUS_METERS")
    
    # External Services
    USER_SERVICE_URL: str = Field(
        default="http://localhost:8081",
        env="USER_SERVICE_URL"
    )
    
    class Config:
        env_file = ".env"
        case_sensitive = True


settings = Settings()

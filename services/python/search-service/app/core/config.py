"""Application configuration using Pydantic Settings."""

from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="allow",
    )

    # Service
    service_name: str = "search-service"
    service_port: int = 8085
    debug: bool = False
    environment: Literal["development", "staging", "production"] = "development"

    # Database
    database_url: str
    max_db_connections: int = 30
    min_db_connections: int = 5
    db_pool_recycle: int = 3600

    # Redis
    redis_url: str
    redis_cache_ttl: int = 180
    redis_active_routes_ttl: int = 60

    # External Services
    matchmaking_service_url: str

    # Search Configuration
    max_search_radius_km: float = 20.0
    default_search_radius_km: float = 5.0
    default_page_size: int = 20
    max_page_size: int = 100

    # Logging
    log_level: str = "INFO"
    log_format: Literal["json", "text"] = "json"


@lru_cache
def get_settings() -> Settings:
    """
    Get cached settings instance.

    Returns:
        Settings: Application settings
    """
    return Settings()

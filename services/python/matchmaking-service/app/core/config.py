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
    service_name: str = "matchmaking-service"
    service_port: int = 8084
    debug: bool = False
    environment: Literal["development", "staging", "production"] = "development"

    # Database
    database_url: str
    max_db_connections: int = 50
    min_db_connections: int = 10
    db_pool_recycle: int = 3600

    # Redis
    redis_url: str
    redis_cache_ttl: int = 300
    redis_active_routes_ttl: int = 60

    # Security
    secret_key: str
    algorithm: str = "HS256"
    access_token_expire_minutes: int = 30

    # External Services
    user_service_url: str
    driver_service_url: str

    # Matching Configuration
    max_search_radius_km: float = 10.0
    default_search_radius_km: float = 5.0
    time_window_minutes: int = 15
    max_candidate_routes: int = 50

    # Scoring Weights (must sum to 1.0)
    weight_route_match: float = 0.4
    weight_time_match: float = 0.3
    weight_rating: float = 0.2
    weight_price: float = 0.1

    # Performance
    performance_target_ms: int = 200

    # Logging
    log_level: str = "INFO"
    log_format: Literal["json", "text"] = "json"

    def validate_weights(self) -> None:
        """Ensure scoring weights sum to 1.0."""
        total = (
            self.weight_route_match
            + self.weight_time_match
            + self.weight_rating
            + self.weight_price
        )
        if abs(total - 1.0) > 0.01:  # Allow small floating-point errors
            raise ValueError(f"Scoring weights must sum to 1.0, got {total}")


@lru_cache
def get_settings() -> Settings:
    """
    Get cached settings instance.

    Returns:
        Settings: Application settings
    """
    settings = Settings()
    settings.validate_weights()
    return settings

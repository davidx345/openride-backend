"""Application configuration using Pydantic Settings."""
from typing import Optional
from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings with validation."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore"
    )

    # Application
    app_name: str = Field(default="openride-notification-service")
    app_env: str = Field(default="development")
    app_version: str = Field(default="1.0.0")
    log_level: str = Field(default="INFO")

    # Server
    host: str = Field(default="0.0.0.0")
    port: int = Field(default=8095)

    # Database
    database_url: str = Field(
        default="postgresql+asyncpg://openride_user:openride_password@localhost:5432/openride"
    )
    database_pool_size: int = Field(default=20)
    database_max_overflow: int = Field(default=10)

    # Redis
    redis_url: str = Field(
        default="redis://:openride_redis_password@localhost:6379/3"
    )
    redis_max_connections: int = Field(default=50)

    # Celery
    celery_broker_url: str = Field(
        default="redis://:openride_redis_password@localhost:6379/4"
    )
    celery_result_backend: str = Field(
        default="redis://:openride_redis_password@localhost:6379/5"
    )

    # JWT
    jwt_secret_key: str = Field(default="your-super-secret-jwt-key-change-in-production")
    jwt_algorithm: str = Field(default="HS256")

    # Firebase Cloud Messaging
    firebase_credentials_path: str = Field(default="./config/firebase-credentials.json")
    fcm_enabled: bool = Field(default=True)

    # Termii SMS
    termii_api_key: str = Field(default="")
    termii_sender_id: str = Field(default="OpenRide")
    termii_api_url: str = Field(default="https://api.ng.termii.com/api")
    termii_enabled: bool = Field(default=True)

    # SendGrid Email
    sendgrid_api_key: str = Field(default="")
    sendgrid_from_email: str = Field(default="noreply@openride.com")
    sendgrid_from_name: str = Field(default="OpenRide")
    sendgrid_enabled: bool = Field(default=True)

    # Notification Settings
    notification_retry_max_attempts: int = Field(default=3)
    notification_retry_delay_seconds: int = Field(default=60)
    notification_batch_size: int = Field(default=100)
    notification_rate_limit_per_user: int = Field(default=50)

    # Template Settings
    template_cache_enabled: bool = Field(default=True)
    template_cache_ttl: int = Field(default=3600)

    # Service URLs
    user_service_url: str = Field(default="http://localhost:8082")
    booking_service_url: str = Field(default="http://localhost:8083")

    # Monitoring
    enable_metrics: bool = Field(default=True)
    metrics_port: int = Field(default=9095)

    @field_validator("log_level")
    @classmethod
    def validate_log_level(cls, v: str) -> str:
        """Validate log level."""
        valid_levels = ["DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"]
        v_upper = v.upper()
        if v_upper not in valid_levels:
            raise ValueError(f"log_level must be one of {valid_levels}")
        return v_upper

    @field_validator("app_env")
    @classmethod
    def validate_environment(cls, v: str) -> str:
        """Validate environment."""
        valid_envs = ["development", "staging", "production"]
        if v not in valid_envs:
            raise ValueError(f"app_env must be one of {valid_envs}")
        return v


# Global settings instance
settings = Settings()

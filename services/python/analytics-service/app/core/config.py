"""Application configuration settings."""

from typing import List

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    # Application
    APP_NAME: str = "openride-analytics-service"
    APP_ENV: str = "development"
    LOG_LEVEL: str = "INFO"
    DEBUG: bool = False

    # Server
    HOST: str = "0.0.0.0"
    PORT: int = 8097

    # Database (PostgreSQL)
    DATABASE_URL: str = "postgresql+asyncpg://openride:openride123@localhost:5432/openride"
    DB_POOL_SIZE: int = 20
    DB_MAX_OVERFLOW: int = 10
    DB_POOL_TIMEOUT: int = 30
    DB_POOL_RECYCLE: int = 3600

    # ClickHouse
    CLICKHOUSE_HOST: str = "localhost"
    CLICKHOUSE_PORT: int = 8123
    CLICKHOUSE_USER: str = "default"
    CLICKHOUSE_PASSWORD: str = ""
    CLICKHOUSE_DATABASE: str = "openride_analytics"
    CLICKHOUSE_SECURE: bool = False

    # Kafka
    KAFKA_BOOTSTRAP_SERVERS: str = "localhost:9092"
    KAFKA_CONSUMER_GROUP_ID: str = "analytics-service-group"
    KAFKA_AUTO_OFFSET_RESET: str = "earliest"
    KAFKA_ENABLE_AUTO_COMMIT: bool = False
    KAFKA_MAX_POLL_RECORDS: int = 500
    KAFKA_SESSION_TIMEOUT_MS: int = 30000
    KAFKA_REQUEST_TIMEOUT_MS: int = 40000

    # Kafka Topics
    KAFKA_TOPIC_USER_EVENTS: str = "openride.events.user"
    KAFKA_TOPIC_ROUTE_EVENTS: str = "openride.events.route"
    KAFKA_TOPIC_BOOKING_EVENTS: str = "openride.events.booking"
    KAFKA_TOPIC_PAYMENT_EVENTS: str = "openride.events.payment"
    KAFKA_TOPIC_TRIP_EVENTS: str = "openride.events.trip"
    KAFKA_TOPIC_LOCATION_EVENTS: str = "openride.events.location"
    KAFKA_TOPIC_DLQ: str = "openride.events.dlq"

    # Redis
    REDIS_URL: str = "redis://localhost:6379/2"
    REDIS_MAX_CONNECTIONS: int = 50
    REDIS_SOCKET_TIMEOUT: int = 5
    REDIS_SOCKET_CONNECT_TIMEOUT: int = 5
    REDIS_RETRY_ON_TIMEOUT: bool = True
    REDIS_DECODE_RESPONSES: bool = True

    # Cache TTL (seconds)
    CACHE_TTL_METRICS: int = 300
    CACHE_TTL_REPORTS: int = 3600
    CACHE_TTL_AGGREGATIONS: int = 1800

    # JWT
    JWT_SECRET_KEY: str = "your-secret-key-change-in-production"
    JWT_ALGORITHM: str = "HS256"
    JWT_ACCESS_TOKEN_EXPIRE_MINUTES: int = 60

    # Celery
    CELERY_BROKER_URL: str = "redis://localhost:6379/3"
    CELERY_RESULT_BACKEND: str = "redis://localhost:6379/3"
    CELERY_TASK_SERIALIZER: str = "json"
    CELERY_RESULT_SERIALIZER: str = "json"
    CELERY_ACCEPT_CONTENT: List[str] = ["json"]
    CELERY_TIMEZONE: str = "Africa/Lagos"
    CELERY_ENABLE_UTC: bool = True

    # Email
    SMTP_HOST: str = "smtp.gmail.com"
    SMTP_PORT: int = 587
    SMTP_USER: str = "noreply@openride.com"
    SMTP_PASSWORD: str = ""
    SMTP_FROM_EMAIL: str = "noreply@openride.com"
    SMTP_USE_TLS: bool = True

    # Metrics
    METRICS_RETENTION_DAYS: int = 90
    AGGREGATION_BATCH_SIZE: int = 1000
    ENABLE_PROMETHEUS_METRICS: bool = True
    PROMETHEUS_PORT: int = 9097

    # CORS
    CORS_ORIGINS: str = "http://localhost:3000,http://localhost:8080"
    CORS_ALLOW_CREDENTIALS: bool = True
    CORS_ALLOW_METHODS: str = "GET,POST,PUT,DELETE,OPTIONS"
    CORS_ALLOW_HEADERS: str = "*"

    # Rate Limiting
    RATE_LIMIT_ENABLED: bool = True
    RATE_LIMIT_PER_MINUTE: int = 60

    # Data Export
    EXPORT_MAX_ROWS: int = 100000
    EXPORT_TEMP_DIR: str = "/tmp/openride-exports"
    EXPORT_RETENTION_HOURS: int = 24

    # Monitoring
    ENABLE_REQUEST_LOGGING: bool = True
    ENABLE_PERFORMANCE_MONITORING: bool = True
    SLOW_QUERY_THRESHOLD_MS: int = 1000

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore",
    )

    @property
    def kafka_topics(self) -> List[str]:
        """Get list of all Kafka topics to subscribe to."""
        return [
            self.KAFKA_TOPIC_USER_EVENTS,
            self.KAFKA_TOPIC_ROUTE_EVENTS,
            self.KAFKA_TOPIC_BOOKING_EVENTS,
            self.KAFKA_TOPIC_PAYMENT_EVENTS,
            self.KAFKA_TOPIC_TRIP_EVENTS,
            self.KAFKA_TOPIC_LOCATION_EVENTS,
        ]

    @property
    def cors_origins_list(self) -> List[str]:
        """Get CORS origins as list."""
        return [origin.strip() for origin in self.CORS_ORIGINS.split(",")]

    @property
    def clickhouse_url(self) -> str:
        """Get ClickHouse connection URL."""
        protocol = "https" if self.CLICKHOUSE_SECURE else "http"
        auth = f"{self.CLICKHOUSE_USER}:{self.CLICKHOUSE_PASSWORD}@" if self.CLICKHOUSE_PASSWORD else ""
        return f"{protocol}://{auth}{self.CLICKHOUSE_HOST}:{self.CLICKHOUSE_PORT}/{self.CLICKHOUSE_DATABASE}"


settings = Settings()

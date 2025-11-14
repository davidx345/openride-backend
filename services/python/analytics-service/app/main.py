"""FastAPI application entry point."""

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import analytics, exports, health, metrics, reports
from app.core import clickhouse_manager, configure_logging, redis_manager, settings
from app.core.logging import get_logger

configure_logging()
logger = get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan manager."""
    # Startup
    logger.info("application_startup", env=settings.APP_ENV)
    
    try:
        # Connect to Redis
        await redis_manager.connect()
        
        # Connect to ClickHouse
        clickhouse_manager.connect()
        
        logger.info("application_ready")
        
        yield
        
    finally:
        # Shutdown
        logger.info("application_shutdown")
        
        # Disconnect Redis
        await redis_manager.disconnect()
        
        # Disconnect ClickHouse
        clickhouse_manager.disconnect()


# Create FastAPI app
app = FastAPI(
    title="OpenRide Analytics Service",
    description="Real-time event processing and business intelligence service",
    version="1.0.0",
    lifespan=lifespan,
)

# Configure CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_credentials=settings.CORS_ALLOW_CREDENTIALS,
    allow_methods=settings.CORS_ALLOW_METHODS.split(","),
    allow_headers=[settings.CORS_ALLOW_HEADERS],
)

# Include routers
app.include_router(health.router, tags=["Health"])
app.include_router(metrics.router, prefix="/v1", tags=["Metrics"])
app.include_router(analytics.router, prefix="/v1", tags=["Analytics"])
app.include_router(exports.router, prefix="/v1", tags=["Exports"])
app.include_router(reports.router, prefix="/v1", tags=["Reports"])


@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "service": "openride-analytics-service",
        "version": "1.0.0",
        "status": "running",
    }

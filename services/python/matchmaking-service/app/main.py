"""FastAPI application entry point."""

import logging
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1 import matching
from app.core.cache import cache_manager
from app.core.config import get_settings
from app.core.logging_config import setup_logging

settings = get_settings()
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """
    Lifespan context manager for startup and shutdown events.

    Args:
        app: FastAPI application

    Yields:
        None
    """
    # Startup
    logger.info(f"Starting {settings.service_name} v1.0.0")
    logger.info(f"Environment: {settings.environment}")

    # Initialize Redis cache
    try:
        await cache_manager.connect()
    except Exception as e:
        logger.warning(f"Redis connection failed: {e}")

    yield

    # Shutdown
    logger.info(f"Shutting down {settings.service_name}")
    await cache_manager.disconnect()


# Setup logging
setup_logging()

# Create FastAPI app
app = FastAPI(
    title="OpenRide Matchmaking Service",
    description="Intelligent route matching and ranking engine",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure appropriately for production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(matching.router, prefix="/v1")


@app.get("/health", tags=["health"])
async def health_check() -> dict[str, str]:
    """
    Health check endpoint.

    Returns:
        dict: Health status
    """
    return {"status": "healthy", "service": settings.service_name}


@app.get("/", tags=["root"])
async def root() -> dict[str, str]:
    """
    Root endpoint.

    Returns:
        dict: Service information
    """
    return {
        "service": settings.service_name,
        "version": "1.0.0",
        "docs": "/docs",
    }

"""FastAPI application entry point for Search Service."""

import logging
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1 import routes
from app.core.cache import cache_manager
from app.core.config import get_settings
from app.core.logging_config import setup_logging

settings = get_settings()
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Lifespan context manager for startup/shutdown."""
    # Startup
    logger.info(f"Starting {settings.service_name} v1.0.0")
    logger.info(f"Environment: {settings.environment}")
    
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
    title="OpenRide Search & Discovery Service",
    description="Public API for searching and discovering available routes",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc",
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(routes.router, prefix="/v1")


@app.get("/health", tags=["health"])
async def health_check() -> dict[str, str]:
    """Health check endpoint."""
    return {"status": "healthy", "service": settings.service_name}


@app.get("/", tags=["root"])
async def root() -> dict[str, str]:
    """Root endpoint."""
    return {
        "service": settings.service_name,
        "version": "1.0.0",
        "docs": "/docs",
    }

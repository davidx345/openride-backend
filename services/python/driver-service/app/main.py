"""
FastAPI application entry point for Driver Service.
"""
from contextlib import asynccontextmanager
from typing import AsyncGenerator

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from prometheus_client import make_asgi_app

from app.api.v1 import vehicles, routes, stops
from app.core.config import settings
from app.core.database import engine, Base
from app.core.logging_config import setup_logging


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator:
    """
    Application lifespan manager.
    Handles startup and shutdown events.
    """
    # Startup
    setup_logging()
    
    # Create tables (in production, use Alembic migrations)
    # async with engine.begin() as conn:
    #     await conn.run_sync(Base.metadata.create_all)
    
    yield
    
    # Shutdown
    await engine.dispose()


app = FastAPI(
    title="OpenRide Driver Service",
    description="Route and Vehicle Management API",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs" if settings.ENVIRONMENT != "production" else None,
    redoc_url="/redoc" if settings.ENVIRONMENT != "production" else None,
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include routers
app.include_router(vehicles.router, prefix="/v1/drivers", tags=["vehicles"])
app.include_router(routes.router, prefix="/v1/drivers", tags=["routes"])
app.include_router(stops.router, prefix="/v1/stops", tags=["stops"])

# Prometheus metrics endpoint
metrics_app = make_asgi_app()
app.mount("/metrics", metrics_app)


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "service": "driver-service"}


@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "service": "OpenRide Driver Service",
        "version": "1.0.0",
        "status": "operational",
    }

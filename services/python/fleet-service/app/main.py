"""FastAPI application entry point"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import drivers, health, trips
from app.core.config import settings
from app.core.logging import configure_logging, get_logger
from app.core.redis import redis_manager
from app.db import db_manager
from app.websocket.server import initialize_services, socket_app

# Configure logging
configure_logging()
logger = get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Application lifespan manager"""
    # Startup
    logger.info(f"Starting {settings.SERVICE_NAME}")
    
    # Initialize database
    db_manager.initialize()
    logger.info("Database initialized")
    
    # Initialize Redis
    await redis_manager.initialize()
    logger.info("Redis initialized")
    
    # Initialize WebSocket services
    async with db_manager.session() as db:
        await initialize_services(db, redis_manager.client)
    
    logger.info(f"{settings.SERVICE_NAME} started successfully")
    
    yield
    
    # Shutdown
    logger.info(f"Shutting down {settings.SERVICE_NAME}")
    
    await redis_manager.close()
    await db_manager.close()
    
    logger.info(f"{settings.SERVICE_NAME} stopped")


# Create FastAPI app
app = FastAPI(
    title="OpenRide Fleet Service",
    description="Real-time fleet tracking and monitoring service",
    version="1.0.0",
    lifespan=lifespan,
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Register REST API routers
app.include_router(health.router)
app.include_router(drivers.router)
app.include_router(trips.router)

# Mount Socket.IO app
app.mount(settings.SOCKETIO_PATH, socket_app)


@app.get("/")
async def root():
    """Root endpoint"""
    return {
        "service": settings.SERVICE_NAME,
        "version": "1.0.0",
        "description": "OpenRide Fleet Tracking Service",
        "endpoints": {
            "rest_api": "/v1",
            "websocket": settings.SOCKETIO_PATH,
            "health": "/health",
            "docs": "/docs",
        }
    }

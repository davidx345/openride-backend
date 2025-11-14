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
from app.core.redis import redis_client
from app.repositories.hub_repository import HubRepository
from app.repositories.route_repository import RouteRepository
from app.services.route_cache_service import RouteCacheService
from app.services.stats_refresh_service import stats_refresh_service
from app.services.cache_invalidation_listener import cache_invalidation_listener
from app.core.database import AsyncSessionLocal

settings = get_settings()
logger = logging.getLogger(__name__)


async def warm_route_cache() -> None:
    """Warm up route cache with popular hub pairs on startup."""
    try:
        logger.info("Starting route cache warming...")
        async with AsyncSessionLocal() as db:
            hub_repo = HubRepository(db)
            route_repo = RouteRepository(db)
            route_cache = RouteCacheService(redis_client)

            # Get top 10 most popular hubs
            all_hubs = await hub_repo.get_all_active_hubs()
            top_hubs = all_hubs[:10]  # Simple heuristic: first 10 active hubs

            # Pre-cache routes for popular hub pairs
            warmed_count = 0
            for origin_hub in top_hubs:
                for dest_hub in top_hubs:
                    if origin_hub.id == dest_hub.id:
                        continue

                    # Query routes for hub pair
                    routes = await route_repo.find_routes_by_hubs(
                        origin_hub_id=origin_hub.id,
                        destination_hub_id=dest_hub.id,
                        active_only=True,
                    )

                    if routes:
                        # Cache with active routes TTL (60s)
                        await route_cache.cache_routes(
                            routes=routes,
                            origin_hub_id=origin_hub.id,
                            destination_hub_id=dest_hub.id,
                            departure_time=None,
                            active_only=True,
                            ttl=settings.redis_active_routes_ttl,
                        )
                        warmed_count += 1

            logger.info(f"Route cache warming complete: {warmed_count} hub pairs cached")
    except Exception as e:
        logger.warning(f"Route cache warming failed: {e}")


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

    # Initialize Redis connections
    try:
        await cache_manager.connect()
        await redis_client.connect()
        logger.info("Redis clients connected")

        # Start stats refresh service
        stats_refresh_service.start()

        # Start cache invalidation listener
        await cache_invalidation_listener.start()

        # Warm route cache
        await warm_route_cache()
    except Exception as e:
        logger.warning(f"Redis initialization failed: {e}")

    yield

    # Shutdown
    logger.info(f"Shutting down {settings.service_name}")
    stats_refresh_service.stop()
    await cache_invalidation_listener.stop()
    await cache_manager.disconnect()
    await redis_client.disconnect()


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

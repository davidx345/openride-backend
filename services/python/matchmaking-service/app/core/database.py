"""Database connection and session management."""

import logging
from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import declarative_base
from sqlalchemy.pool import NullPool

from app.core.config import get_settings

settings = get_settings()
logger = logging.getLogger(__name__)


def get_async_database_url(url: str) -> str:
    """Convert database URL to async-compatible format with asyncpg driver."""
    if url.startswith("postgresql+asyncpg://"):
        return url
    if url.startswith("postgresql://"):
        return url.replace("postgresql://", "postgresql+asyncpg://", 1)
    if url.startswith("postgres://"):
        return url.replace("postgres://", "postgresql+asyncpg://", 1)
    return url


# Get async-compatible database URL
ASYNC_DATABASE_URL = get_async_database_url(settings.database_url)

# Create primary async engine with NullPool for transaction pooler compatibility
# NullPool is required when using external transaction poolers (PgBouncer/Supabase)
# to prevent double pooling and connection state issues
engine = create_async_engine(
    ASYNC_DATABASE_URL,
    poolclass=NullPool,
    echo=settings.debug,
    pool_pre_ping=True,
)

# Create read replica engine if configured
replica_engine = None
if settings.replica_database_url:
    logger.info("Initializing read replica connection pool")
    replica_url = get_async_database_url(settings.replica_database_url)
    replica_engine = create_async_engine(
        replica_url,
        poolclass=NullPool,
        echo=settings.debug,
        pool_pre_ping=True,
    )

# Create async session maker
AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autocommit=False,
    autoflush=False,
)

# Create read replica session maker
AsyncSessionReplica = None
if replica_engine:
    AsyncSessionReplica = async_sessionmaker(
        replica_engine,
        class_=AsyncSession,
        expire_on_commit=False,
        autocommit=False,
        autoflush=False,
    )

# Base class for SQLAlchemy models
Base = declarative_base()


async def get_db(read_only: bool = False) -> AsyncGenerator[AsyncSession, None]:
    """
    Dependency for getting async database sessions.

    Args:
        read_only: If True and replica is configured, use read replica

    Yields:
        AsyncSession: Database session
    """
    # Use replica for read-only queries if available
    session_maker = AsyncSessionLocal
    if read_only and AsyncSessionReplica:
        session_maker = AsyncSessionReplica
        logger.debug("Using read replica for query")
    
    async with session_maker() as session:
        try:
            yield session
        finally:
            await session.close()


@asynccontextmanager
async def get_db_context(read_only: bool = False) -> AsyncGenerator[AsyncSession, None]:
    """
    Context manager for getting async database sessions.

    Args:
        read_only: If True and replica is configured, use read replica

    Yields:
        AsyncSession: Database session
    """
    session_maker = AsyncSessionLocal
    if read_only and AsyncSessionReplica:
        session_maker = AsyncSessionReplica
    
    async with session_maker() as session:
        try:
            yield session
        finally:
            await session.close()


def get_pool_status() -> dict:
    """
    Get current connection pool status for monitoring.

    Returns:
        dict: Pool statistics
    """
    pool = engine.pool
    
    status = {
        "primary": {
            "size": pool.size(),
            "checked_in": pool.checkedin(),
            "checked_out": pool.size() - pool.checkedin(),
            "overflow": pool.overflow(),
            "total_connections": pool.size() + pool.overflow(),
        }
    }
    
    if replica_engine:
        replica_pool = replica_engine.pool
        status["replica"] = {
            "size": replica_pool.size(),
            "checked_in": replica_pool.checkedin(),
            "checked_out": replica_pool.size() - replica_pool.checkedin(),
            "overflow": replica_pool.overflow(),
            "total_connections": replica_pool.size() + replica_pool.overflow(),
        }
    
    return status

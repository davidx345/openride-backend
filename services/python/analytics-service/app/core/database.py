"""Database connection and session management for analytics service.

This module provides async database session management compatible with SQLAlchemy 2.0.
It handles connection pooling and ensures proper async driver usage (asyncpg).
"""

from contextlib import asynccontextmanager
from typing import AsyncGenerator

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import declarative_base
from sqlalchemy.pool import NullPool

from app.core.config import settings


def get_async_database_url(url: str) -> str:
    """Convert database URL to async-compatible format.
    
    Ensures the URL uses the asyncpg driver for async SQLAlchemy.
    Handles various input formats:
    - postgresql://... -> postgresql+asyncpg://...
    - postgres://... -> postgresql+asyncpg://...
    - postgresql+asyncpg://... -> unchanged
    
    Args:
        url: Database connection URL
        
    Returns:
        Async-compatible database URL with asyncpg driver
    """
    if url.startswith("postgresql+asyncpg://"):
        return url
    if url.startswith("postgresql://"):
        return url.replace("postgresql://", "postgresql+asyncpg://", 1)
    if url.startswith("postgres://"):
        return url.replace("postgres://", "postgresql+asyncpg://", 1)
    return url


# Get async-compatible database URL
ASYNC_DATABASE_URL = get_async_database_url(settings.DATABASE_URL)

# Create async engine with NullPool for transaction pooler compatibility
# NullPool is required when using external transaction poolers (PgBouncer/Supabase)
# to prevent double pooling and connection state issues
engine = create_async_engine(
    ASYNC_DATABASE_URL,
    poolclass=NullPool,
    echo=settings.DEBUG,
    pool_pre_ping=True,
)

# Create async session factory
AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autocommit=False,
    autoflush=False,
)

# Base class for SQLAlchemy models
Base = declarative_base()


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    """FastAPI dependency to get database session.
    
    Yields:
        AsyncSession: Database session
        
    Usage:
        @app.get("/items")
        async def get_items(db: AsyncSession = Depends(get_db)):
            ...
    """
    async with AsyncSessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()


@asynccontextmanager
async def get_db_context() -> AsyncGenerator[AsyncSession, None]:
    """Context manager to get database session.
    
    Yields:
        AsyncSession: Database session
        
    Usage:
        async with get_db_context() as db:
            result = await db.execute(query)
    """
    async with AsyncSessionLocal() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
        finally:
            await session.close()


async def init_db() -> None:
    """Initialize database connection.
    
    Call this on application startup.
    """
    async with engine.begin() as conn:
        # Import all models to ensure they're registered
        from app.db import models  # noqa: F401
        
        # Create all tables (development only)
        # In production, use Alembic migrations
        if settings.DEBUG:
            await conn.run_sync(Base.metadata.create_all)


async def close_db() -> None:
    """Close database connection pool.
    
    Call this on application shutdown.
    """
    await engine.dispose()

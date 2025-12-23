"""
Database utilities for OpenRide Python services.
"""

from typing import AsyncGenerator
from sqlalchemy.ext.asyncio import (
    AsyncSession,
    create_async_engine,
    async_sessionmaker,
)
from sqlalchemy.orm import declarative_base
from sqlalchemy.pool import NullPool

Base = declarative_base()


class DatabaseManager:
    """Manager for database connections and sessions."""

    def __init__(self, database_url: str, echo: bool = False):
        """
        Initialize database manager.

        Args:
            database_url: Database connection URL
            echo: Whether to echo SQL statements (for debugging)
        """
        # Use NullPool for transaction pooler compatibility
        # NullPool is required when using external transaction poolers (PgBouncer/Supabase)
        # to prevent double pooling and connection state issues
        self.engine = create_async_engine(
            database_url,
            echo=echo,
            poolclass=NullPool,
            pool_pre_ping=True,
        )
        self.async_session_factory = async_sessionmaker(
            self.engine,
            class_=AsyncSession,
            expire_on_commit=False,
        )

    async def get_session(self) -> AsyncGenerator[AsyncSession, None]:
        """
        Get a database session.

        Yields:
            AsyncSession instance
        """
        async with self.async_session_factory() as session:
            try:
                yield session
            except Exception:
                await session.rollback()
                raise
            finally:
                await session.close()

    async def close(self):
        """Close database connections."""
        await self.engine.dispose()


async def get_db_session(
    db_manager: DatabaseManager,
) -> AsyncGenerator[AsyncSession, None]:
    """
    Dependency for FastAPI to get database sessions.

    Args:
        db_manager: Database manager instance

    Yields:
        Database session
    """
    async for session in db_manager.get_session():
        yield session

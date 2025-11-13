"""Test fixtures and configuration"""
import pytest
import pytest_asyncio
from typing import AsyncGenerator
from unittest.mock import AsyncMock, Mock

from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine, async_sessionmaker
from sqlalchemy.pool import NullPool

from app.db.models import Base


DATABASE_URL = "postgresql+asyncpg://openride_user:openride_password@localhost:5432/openride_test"


@pytest.fixture(scope="session")
def event_loop():
    """Create event loop for async tests"""
    import asyncio
    loop = asyncio.get_event_loop_policy().new_event_loop()
    yield loop
    loop.close()


@pytest_asyncio.fixture
async def db_engine():
    """Create test database engine"""
    engine = create_async_engine(
        DATABASE_URL,
        poolclass=NullPool,
    )
    
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    
    yield engine
    
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
    
    await engine.dispose()


@pytest_asyncio.fixture
async def db_session(db_engine) -> AsyncGenerator[AsyncSession, None]:
    """Create test database session"""
    async_session = async_sessionmaker(
        db_engine,
        class_=AsyncSession,
        expire_on_commit=False,
    )
    
    async with async_session() as session:
        yield session
        await session.rollback()


@pytest.fixture
def redis_client():
    """Create mock Redis client"""
    mock_redis = AsyncMock()
    mock_redis.ping = AsyncMock(return_value=True)
    mock_redis.setex = AsyncMock(return_value=True)
    mock_redis.exists = AsyncMock(return_value=False)
    mock_redis.publish = AsyncMock(return_value=1)
    return mock_redis


@pytest.fixture
def mock_sio():
    """Create mock Socket.IO server"""
    mock = AsyncMock()
    mock.emit = AsyncMock()
    mock.enter_room = AsyncMock()
    mock.leave_room = AsyncMock()
    return mock

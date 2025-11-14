"""Pytest configuration and fixtures."""
import asyncio
import pytest
import pytest_asyncio
from typing import AsyncGenerator, Generator
from uuid import uuid4
from sqlalchemy.ext.asyncio import AsyncSession, create_async_engine, async_sessionmaker
from sqlalchemy.pool import NullPool
from httpx import AsyncClient
from app.main import app
from app.database import Base, get_db_session
from app.config import settings
from app.auth import JWTAuth

# Test database URL
TEST_DATABASE_URL = "postgresql+asyncpg://openride_user:openride_password@localhost:5432/openride_test"

# Create test engine
test_engine = create_async_engine(
    TEST_DATABASE_URL,
    poolclass=NullPool,
    echo=False,
)

# Create test session maker
test_async_session_maker = async_sessionmaker(
    test_engine,
    class_=AsyncSession,
    expire_on_commit=False,
)


@pytest.fixture(scope="session")
def event_loop() -> Generator:
    """Create event loop for async tests."""
    loop = asyncio.get_event_loop_policy().new_event_loop()
    yield loop
    loop.close()


@pytest_asyncio.fixture(scope="function")
async def db_session() -> AsyncGenerator[AsyncSession, None]:
    """Create test database session."""
    # Create tables
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    # Create session
    async with test_async_session_maker() as session:
        yield session
        await session.rollback()

    # Drop tables
    async with test_engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)


@pytest_asyncio.fixture
async def client(db_session: AsyncSession) -> AsyncGenerator[AsyncClient, None]:
    """Create test HTTP client."""

    async def override_get_db():
        yield db_session

    app.dependency_overrides[get_db_session] = override_get_db

    async with AsyncClient(app=app, base_url="http://test") as ac:
        yield ac

    app.dependency_overrides.clear()


@pytest.fixture
def test_user_id():
    """Generate test user ID."""
    return uuid4()


@pytest.fixture
def test_token(test_user_id):
    """Generate test JWT token."""
    return JWTAuth.create_access_token(
        user_id=test_user_id,
        additional_claims={"roles": ["user"]},
    )


@pytest.fixture
def test_admin_token(test_user_id):
    """Generate test admin JWT token."""
    return JWTAuth.create_access_token(
        user_id=test_user_id,
        additional_claims={"roles": ["admin", "user"]},
    )


@pytest.fixture
def auth_headers(test_token):
    """Create authorization headers."""
    return {"Authorization": f"Bearer {test_token}"}


@pytest.fixture
def admin_auth_headers(test_admin_token):
    """Create admin authorization headers."""
    return {"Authorization": f"Bearer {test_admin_token}"}


@pytest.fixture
def mock_fcm_service(mocker):
    """Mock FCM service."""
    return mocker.patch("app.services.fcm_service.FCMService")


@pytest.fixture
def mock_termii_service(mocker):
    """Mock Termii SMS service."""
    return mocker.patch("app.services.termii_service.TermiiSMSService")


@pytest.fixture
def mock_sendgrid_service(mocker):
    """Mock SendGrid email service."""
    return mocker.patch("app.services.email_service.SendGridEmailService")

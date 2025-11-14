"""Tests for FCM token API endpoints."""
import pytest
from uuid import uuid4
from httpx import AsyncClient


class TestTokenAPI:
    """Test FCM token API endpoints."""

    @pytest.mark.asyncio
    async def test_register_token_unauthorized(self, client: AsyncClient):
        """Test registering token without auth."""
        response = await client.post(
            "/v1/notifications/tokens",
            json={
                "fcm_token": "test_fcm_token_12345",
                "device_id": "device_123",
                "platform": "ios",
            },
        )
        assert response.status_code == 403

    @pytest.mark.asyncio
    async def test_register_token_success(
        self, client: AsyncClient, auth_headers, test_user_id
    ):
        """Test registering FCM token with auth."""
        response = await client.post(
            "/v1/notifications/tokens",
            headers=auth_headers,
            json={
                "fcm_token": "test_fcm_token_67890",
                "device_id": "device_456",
                "platform": "android",
            },
        )
        assert response.status_code == 200
        data = response.json()
        assert data["fcm_token"] == "test_fcm_token_67890"
        assert data["device_id"] == "device_456"

    @pytest.mark.asyncio
    async def test_register_duplicate_token(
        self, client: AsyncClient, auth_headers, test_user_id, db_session
    ):
        """Test registering duplicate FCM token."""
        # Create existing token
        from app.models import FCMToken
        token = FCMToken(
            user_id=test_user_id,
            fcm_token="duplicate_token",
            device_id="device_999",
            platform="ios",
        )
        db_session.add(token)
        await db_session.commit()

        response = await client.post(
            "/v1/notifications/tokens",
            headers=auth_headers,
            json={
                "fcm_token": "duplicate_token",
                "device_id": "device_999",
                "platform": "ios",
            },
        )
        # Should update existing token
        assert response.status_code == 200

    @pytest.mark.asyncio
    async def test_get_user_tokens(
        self, client: AsyncClient, auth_headers, test_user_id, db_session
    ):
        """Test getting user's FCM tokens."""
        # Create test tokens
        from app.models import FCMToken
        token1 = FCMToken(
            user_id=test_user_id,
            fcm_token="token1",
            device_id="device1",
            platform="ios",
        )
        token2 = FCMToken(
            user_id=test_user_id,
            fcm_token="token2",
            device_id="device2",
            platform="android",
        )
        db_session.add_all([token1, token2])
        await db_session.commit()

        response = await client.get(
            "/v1/notifications/tokens",
            headers=auth_headers,
        )
        assert response.status_code == 200
        data = response.json()
        assert len(data) >= 2

    @pytest.mark.asyncio
    async def test_delete_token_success(
        self, client: AsyncClient, auth_headers, test_user_id, db_session
    ):
        """Test deleting FCM token."""
        # Create test token
        from app.models import FCMToken
        token = FCMToken(
            user_id=test_user_id,
            fcm_token="token_to_delete",
            device_id="device_delete",
            platform="ios",
        )
        db_session.add(token)
        await db_session.commit()
        await db_session.refresh(token)

        response = await client.delete(
            f"/v1/notifications/tokens/{token.id}",
            headers=auth_headers,
        )
        assert response.status_code == 200

    @pytest.mark.asyncio
    async def test_delete_token_unauthorized(
        self, client: AsyncClient, auth_headers, db_session
    ):
        """Test deleting another user's token."""
        # Create token for different user
        from app.models import FCMToken
        other_user_id = uuid4()
        token = FCMToken(
            user_id=other_user_id,
            fcm_token="other_user_token",
            device_id="other_device",
            platform="android",
        )
        db_session.add(token)
        await db_session.commit()
        await db_session.refresh(token)

        response = await client.delete(
            f"/v1/notifications/tokens/{token.id}",
            headers=auth_headers,
        )
        # Should return 404 since user doesn't own the token
        assert response.status_code == 404

    @pytest.mark.asyncio
    async def test_delete_token_not_found(self, client: AsyncClient, auth_headers):
        """Test deleting non-existent token."""
        response = await client.delete(
            f"/v1/notifications/tokens/{uuid4()}",
            headers=auth_headers,
        )
        assert response.status_code == 404

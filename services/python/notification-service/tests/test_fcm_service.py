"""Tests for FCM service."""
import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from uuid import uuid4
from app.services.fcm_service import FCMService


class TestFCMService:
    """Test FCM push notification service."""

    @pytest.mark.asyncio
    async def test_send_push_notification_success(self, mock_fcm_service):
        """Test sending push notification successfully."""
        fcm_token = "test_fcm_token_123"
        title = "Test Notification"
        body = "This is a test notification"
        data = {"booking_id": "12345", "route": "ABC"}

        with patch("firebase_admin.messaging.send") as mock_send:
            mock_send.return_value = "projects/test/messages/msg_123"
            
            result = await mock_fcm_service.send_push_notification(
                fcm_token, title, body, data
            )
            
            assert result is True
            mock_send.assert_called_once()

    @pytest.mark.asyncio
    async def test_send_push_notification_invalid_token(self, mock_fcm_service):
        """Test sending push notification with invalid token."""
        fcm_token = "invalid_token"
        title = "Test"
        body = "Test body"

        with patch("firebase_admin.messaging.send") as mock_send:
            from firebase_admin.exceptions import InvalidArgumentError
            mock_send.side_effect = InvalidArgumentError("Invalid token")
            
            result = await mock_fcm_service.send_push_notification(
                fcm_token, title, body
            )
            
            assert result is False

    @pytest.mark.asyncio
    async def test_send_push_notification_with_empty_data(self, mock_fcm_service):
        """Test sending push notification with empty data."""
        fcm_token = "test_token"
        title = "Title"
        body = "Body"

        with patch("firebase_admin.messaging.send") as mock_send:
            mock_send.return_value = "msg_id"
            
            result = await mock_fcm_service.send_push_notification(
                fcm_token, title, body, data=None
            )
            
            assert result is True

    @pytest.mark.asyncio
    async def test_send_push_to_multiple_success(self, mock_fcm_service):
        """Test sending push to multiple tokens."""
        fcm_tokens = ["token1", "token2", "token3"]
        title = "Broadcast"
        body = "Broadcast message"

        with patch("firebase_admin.messaging.send_each") as mock_send_each:
            # Mock successful sends
            mock_response = MagicMock()
            mock_response.success_count = 3
            mock_response.failure_count = 0
            mock_send_each.return_value = mock_response
            
            success_count = await mock_fcm_service.send_push_to_multiple(
                fcm_tokens, title, body
            )
            
            assert success_count == 3
            mock_send_each.assert_called_once()

    @pytest.mark.asyncio
    async def test_send_push_to_multiple_partial_failure(self, mock_fcm_service):
        """Test sending push to multiple tokens with partial failures."""
        fcm_tokens = ["token1", "token2", "token3"]
        title = "Test"
        body = "Test body"

        with patch("firebase_admin.messaging.send_each") as mock_send_each:
            mock_response = MagicMock()
            mock_response.success_count = 2
            mock_response.failure_count = 1
            mock_send_each.return_value = mock_response
            
            success_count = await mock_fcm_service.send_push_to_multiple(
                fcm_tokens, title, body
            )
            
            assert success_count == 2

    @pytest.mark.asyncio
    async def test_send_push_to_multiple_all_fail(self, mock_fcm_service):
        """Test sending push to multiple tokens with all failures."""
        fcm_tokens = ["invalid1", "invalid2"]
        title = "Test"
        body = "Test body"

        with patch("firebase_admin.messaging.send_each") as mock_send_each:
            from firebase_admin.exceptions import FirebaseError
            mock_send_each.side_effect = FirebaseError("Auth error", "test")
            
            success_count = await mock_fcm_service.send_push_to_multiple(
                fcm_tokens, title, body
            )
            
            assert success_count == 0

    @pytest.mark.asyncio
    async def test_send_push_to_empty_list(self, mock_fcm_service):
        """Test sending push to empty token list."""
        fcm_tokens = []
        title = "Test"
        body = "Test body"

        success_count = await mock_fcm_service.send_push_to_multiple(
            fcm_tokens, title, body
        )
        
        assert success_count == 0

    @pytest.mark.asyncio
    async def test_validate_fcm_token_valid(self, mock_fcm_service):
        """Test validating valid FCM token."""
        fcm_token = "valid_token_abc123"

        with patch("firebase_admin.messaging.send") as mock_send:
            mock_send.return_value = "msg_id"
            
            is_valid = await mock_fcm_service.validate_fcm_token(fcm_token)
            
            assert is_valid is True

    @pytest.mark.asyncio
    async def test_validate_fcm_token_invalid(self, mock_fcm_service):
        """Test validating invalid FCM token."""
        fcm_token = "invalid_token"

        with patch("firebase_admin.messaging.send") as mock_send:
            from firebase_admin.exceptions import InvalidArgumentError
            mock_send.side_effect = InvalidArgumentError("Invalid token")
            
            is_valid = await mock_fcm_service.validate_fcm_token(fcm_token)
            
            assert is_valid is False

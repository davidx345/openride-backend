"""Tests for Termii SMS service."""
import pytest
from unittest.mock import AsyncMock, patch
from httpx import Response
from app.services.termii_service import TermiiService


class TestTermiiService:
    """Test Termii SMS service."""

    @pytest.mark.asyncio
    async def test_send_sms_success(self, mock_termii_service):
        """Test sending SMS successfully."""
        phone_number = "+2348012345678"
        message = "Your booking is confirmed"

        mock_response = Response(200, json={"message": "Successfully Sent"})
        
        with patch("httpx.AsyncClient.post", return_value=mock_response):
            result = await mock_termii_service.send_sms(phone_number, message)
            
            assert result is True

    @pytest.mark.asyncio
    async def test_send_sms_api_error(self, mock_termii_service):
        """Test sending SMS with API error."""
        phone_number = "+2348012345678"
        message = "Test message"

        mock_response = Response(400, json={"error": "Invalid phone number"})
        
        with patch("httpx.AsyncClient.post", return_value=mock_response):
            result = await mock_termii_service.send_sms(phone_number, message)
            
            assert result is False

    @pytest.mark.asyncio
    async def test_send_sms_network_error(self, mock_termii_service):
        """Test sending SMS with network error."""
        phone_number = "+2348012345678"
        message = "Test message"

        with patch("httpx.AsyncClient.post", side_effect=Exception("Network error")):
            result = await mock_termii_service.send_sms(phone_number, message)
            
            assert result is False

    @pytest.mark.asyncio
    async def test_validate_phone_number_valid(self, mock_termii_service):
        """Test validating valid Nigerian phone number."""
        valid_numbers = [
            "+2348012345678",
            "+2347012345678",
            "+2349012345678",
            "+2348112345678",
        ]

        for phone in valid_numbers:
            is_valid = await mock_termii_service.validate_phone_number(phone)
            assert is_valid is True, f"Phone {phone} should be valid"

    @pytest.mark.asyncio
    async def test_validate_phone_number_invalid(self, mock_termii_service):
        """Test validating invalid phone numbers."""
        invalid_numbers = [
            "+1234567890",  # Not Nigerian
            "08012345678",  # No country code
            "+234801234567",  # Too short
            "+23480123456789",  # Too long
            "+234501234567",  # Invalid prefix
            "not_a_phone_number",
        ]

        for phone in invalid_numbers:
            is_valid = await mock_termii_service.validate_phone_number(phone)
            assert is_valid is False, f"Phone {phone} should be invalid"

    @pytest.mark.asyncio
    async def test_send_sms_with_long_message(self, mock_termii_service):
        """Test sending SMS with long message (multipart)."""
        phone_number = "+2348012345678"
        long_message = "A" * 200  # Longer than 160 chars

        mock_response = Response(200, json={"message": "Successfully Sent"})
        
        with patch("httpx.AsyncClient.post", return_value=mock_response):
            result = await mock_termii_service.send_sms(phone_number, long_message)
            
            assert result is True

    @pytest.mark.asyncio
    async def test_send_sms_empty_message(self, mock_termii_service):
        """Test sending SMS with empty message."""
        phone_number = "+2348012345678"
        message = ""

        # Should not attempt to send
        result = await mock_termii_service.send_sms(phone_number, message)
        assert result is False

    @pytest.mark.asyncio
    async def test_send_sms_invalid_phone(self, mock_termii_service):
        """Test sending SMS to invalid phone number."""
        phone_number = "invalid"
        message = "Test message"

        result = await mock_termii_service.send_sms(phone_number, message)
        assert result is False

    @pytest.mark.asyncio
    async def test_send_bulk_sms(self, mock_termii_service):
        """Test sending bulk SMS."""
        phone_numbers = ["+2348012345678", "+2347012345678", "+2349012345678"]
        message = "Bulk message"

        mock_response = Response(200, json={"message": "Successfully Sent"})
        
        with patch("httpx.AsyncClient.post", return_value=mock_response):
            success_count = 0
            for phone in phone_numbers:
                result = await mock_termii_service.send_sms(phone, message)
                if result:
                    success_count += 1
            
            assert success_count == 3

    @pytest.mark.asyncio
    async def test_send_sms_with_special_characters(self, mock_termii_service):
        """Test sending SMS with special characters."""
        phone_number = "+2348012345678"
        message = "Hello! Your code is: #12345 @ 50% off"

        mock_response = Response(200, json={"message": "Successfully Sent"})
        
        with patch("httpx.AsyncClient.post", return_value=mock_response):
            result = await mock_termii_service.send_sms(phone_number, message)
            
            assert result is True

    @pytest.mark.asyncio
    async def test_send_sms_rate_limit(self, mock_termii_service):
        """Test sending SMS with rate limit error."""
        phone_number = "+2348012345678"
        message = "Test message"

        mock_response = Response(429, json={"error": "Rate limit exceeded"})
        
        with patch("httpx.AsyncClient.post", return_value=mock_response):
            result = await mock_termii_service.send_sms(phone_number, message)
            
            assert result is False

    @pytest.mark.asyncio
    async def test_send_sms_unauthorized(self, mock_termii_service):
        """Test sending SMS with unauthorized error."""
        phone_number = "+2348012345678"
        message = "Test message"

        mock_response = Response(401, json={"error": "Invalid API key"})
        
        with patch("httpx.AsyncClient.post", return_value=mock_response):
            result = await mock_termii_service.send_sms(phone_number, message)
            
            assert result is False

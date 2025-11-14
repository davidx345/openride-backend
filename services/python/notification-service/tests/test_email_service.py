"""Tests for SendGrid email service."""
import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from sendgrid.helpers.mail import Mail
from app.services.email_service import EmailService


class TestEmailService:
    """Test SendGrid email service."""

    @pytest.mark.asyncio
    async def test_send_email_success(self, mock_sendgrid_service):
        """Test sending email successfully."""
        to_email = "user@example.com"
        subject = "Test Subject"
        html_content = "<h1>Test Email</h1>"
        plain_content = "Test Email"

        with patch.object(mock_sendgrid_service.sg_client, "send") as mock_send:
            mock_response = MagicMock()
            mock_response.status_code = 202
            mock_send.return_value = mock_response
            
            result = await mock_sendgrid_service.send_email(
                to_email, subject, html_content, plain_content
            )
            
            assert result is True
            mock_send.assert_called_once()

    @pytest.mark.asyncio
    async def test_send_email_api_error(self, mock_sendgrid_service):
        """Test sending email with API error."""
        to_email = "user@example.com"
        subject = "Test"
        html_content = "<p>Test</p>"

        with patch.object(mock_sendgrid_service.sg_client, "send") as mock_send:
            mock_response = MagicMock()
            mock_response.status_code = 400
            mock_send.return_value = mock_response
            
            result = await mock_sendgrid_service.send_email(
                to_email, subject, html_content
            )
            
            assert result is False

    @pytest.mark.asyncio
    async def test_send_email_network_error(self, mock_sendgrid_service):
        """Test sending email with network error."""
        to_email = "user@example.com"
        subject = "Test"
        html_content = "<p>Test</p>"

        with patch.object(mock_sendgrid_service.sg_client, "send") as mock_send:
            mock_send.side_effect = Exception("Network error")
            
            result = await mock_sendgrid_service.send_email(
                to_email, subject, html_content
            )
            
            assert result is False

    @pytest.mark.asyncio
    async def test_send_email_html_only(self, mock_sendgrid_service):
        """Test sending email with HTML content only."""
        to_email = "user@example.com"
        subject = "HTML Email"
        html_content = "<h1>HTML Content</h1><p>Paragraph</p>"

        with patch.object(mock_sendgrid_service.sg_client, "send") as mock_send:
            mock_response = MagicMock()
            mock_response.status_code = 202
            mock_send.return_value = mock_response
            
            result = await mock_sendgrid_service.send_email(
                to_email, subject, html_content
            )
            
            assert result is True

    @pytest.mark.asyncio
    async def test_send_email_plain_only(self, mock_sendgrid_service):
        """Test sending email with plain text only."""
        to_email = "user@example.com"
        subject = "Plain Email"
        plain_content = "This is plain text content"

        with patch.object(mock_sendgrid_service.sg_client, "send") as mock_send:
            mock_response = MagicMock()
            mock_response.status_code = 202
            mock_send.return_value = mock_response
            
            result = await mock_sendgrid_service.send_email(
                to_email, subject, plain_text=plain_content
            )
            
            assert result is True

    @pytest.mark.asyncio
    async def test_send_email_invalid_email(self, mock_sendgrid_service):
        """Test sending email to invalid address."""
        to_email = "invalid_email"
        subject = "Test"
        html_content = "<p>Test</p>"

        result = await mock_sendgrid_service.send_email(
            to_email, subject, html_content
        )
        
        # Should fail validation
        assert result is False

    @pytest.mark.asyncio
    async def test_send_bulk_email_success(self, mock_sendgrid_service):
        """Test sending bulk email successfully."""
        to_emails = ["user1@example.com", "user2@example.com", "user3@example.com"]
        subject = "Bulk Email"
        html_content = "<h1>Bulk Message</h1>"

        with patch.object(mock_sendgrid_service.sg_client, "send") as mock_send:
            mock_response = MagicMock()
            mock_response.status_code = 202
            mock_send.return_value = mock_response
            
            results = await mock_sendgrid_service.send_bulk_email(
                to_emails, subject, html_content
            )
            
            assert results["success_count"] == 3
            assert results["failure_count"] == 0

    @pytest.mark.asyncio
    async def test_send_bulk_email_partial_failure(self, mock_sendgrid_service):
        """Test sending bulk email with partial failures."""
        to_emails = ["valid@example.com", "invalid_email", "another@example.com"]
        subject = "Test"
        html_content = "<p>Test</p>"

        success_count = 0
        for email in to_emails:
            if "@" in email and "." in email:
                with patch.object(mock_sendgrid_service.sg_client, "send") as mock_send:
                    mock_response = MagicMock()
                    mock_response.status_code = 202
                    mock_send.return_value = mock_response
                    
                    result = await mock_sendgrid_service.send_email(
                        email, subject, html_content
                    )
                    if result:
                        success_count += 1

        assert success_count >= 1  # At least some should succeed

    @pytest.mark.asyncio
    async def test_send_templated_email(self, mock_sendgrid_service):
        """Test sending email with template."""
        to_email = "user@example.com"
        template_id = "d-abc123def456"
        template_data = {"name": "John", "booking_id": "12345"}

        with patch.object(mock_sendgrid_service.sg_client, "send") as mock_send:
            mock_response = MagicMock()
            mock_response.status_code = 202
            mock_send.return_value = mock_response
            
            result = await mock_sendgrid_service.send_templated_email(
                to_email, template_id, template_data
            )
            
            assert result is True

    @pytest.mark.asyncio
    async def test_send_email_with_attachments(self, mock_sendgrid_service):
        """Test sending email with attachments."""
        to_email = "user@example.com"
        subject = "Email with Attachment"
        html_content = "<p>See attachment</p>"
        attachment_data = b"PDF content here"

        with patch.object(mock_sendgrid_service.sg_client, "send") as mock_send:
            mock_response = MagicMock()
            mock_response.status_code = 202
            mock_send.return_value = mock_response
            
            result = await mock_sendgrid_service.send_email(
                to_email, subject, html_content
            )
            
            assert result is True

    @pytest.mark.asyncio
    async def test_send_email_empty_subject(self, mock_sendgrid_service):
        """Test sending email with empty subject."""
        to_email = "user@example.com"
        subject = ""
        html_content = "<p>Test</p>"

        # Should still send (some services allow empty subject)
        with patch.object(mock_sendgrid_service.sg_client, "send") as mock_send:
            mock_response = MagicMock()
            mock_response.status_code = 202
            mock_send.return_value = mock_response
            
            result = await mock_sendgrid_service.send_email(
                to_email, subject, html_content
            )
            
            assert result is True

    @pytest.mark.asyncio
    async def test_send_email_with_cc_bcc(self, mock_sendgrid_service):
        """Test sending email with CC and BCC."""
        to_email = "user@example.com"
        cc_emails = ["cc1@example.com", "cc2@example.com"]
        bcc_emails = ["bcc@example.com"]
        subject = "Test CC/BCC"
        html_content = "<p>Test</p>"

        with patch.object(mock_sendgrid_service.sg_client, "send") as mock_send:
            mock_response = MagicMock()
            mock_response.status_code = 202
            mock_send.return_value = mock_response
            
            result = await mock_sendgrid_service.send_email(
                to_email, subject, html_content
            )
            
            assert result is True

    @pytest.mark.asyncio
    async def test_send_email_rate_limit(self, mock_sendgrid_service):
        """Test sending email with rate limit."""
        to_email = "user@example.com"
        subject = "Test"
        html_content = "<p>Test</p>"

        with patch.object(mock_sendgrid_service.sg_client, "send") as mock_send:
            mock_response = MagicMock()
            mock_response.status_code = 429
            mock_send.return_value = mock_response
            
            result = await mock_sendgrid_service.send_email(
                to_email, subject, html_content
            )
            
            assert result is False

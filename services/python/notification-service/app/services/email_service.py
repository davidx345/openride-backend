"""SendGrid email service for sending email notifications."""
import logging
from typing import Dict, Any, Optional, List
from sendgrid import SendGridAPIClient
from sendgrid.helpers.mail import Mail, Email, To, Content
from app.config import settings

logger = logging.getLogger(__name__)


class EmailService:
    """SendGrid email service."""

    def __init__(self) -> None:
        """Initialize SendGrid service."""
        self.api_key = settings.sendgrid_api_key
        self.from_email = settings.sendgrid_from_email
        self.from_name = settings.sendgrid_from_name
        self.enabled = settings.sendgrid_enabled
        self.client = None

        if self.enabled and self.api_key:
            try:
                self.client = SendGridAPIClient(self.api_key)
                logger.info("SendGrid service initialized successfully")
            except Exception as e:
                logger.error(f"Failed to initialize SendGrid: {e}")
                self.client = None

    async def send_email(
        self,
        to_email: str,
        subject: str,
        html_content: str,
        plain_content: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Send email via SendGrid.

        Args:
            to_email: Recipient email address
            subject: Email subject
            html_content: HTML email content
            plain_content: Plain text email content (optional)

        Returns:
            Dict with success status and message_id or error
        """
        if not self.enabled:
            logger.warning("SendGrid not enabled, skipping email")
            return {"success": False, "error": "Email service not enabled"}

        if not self.client:
            logger.error("SendGrid client not initialized")
            return {"success": False, "error": "Email service not configured"}

        try:
            message = Mail(
                from_email=Email(self.from_email, self.from_name),
                to_emails=To(to_email),
                subject=subject,
                html_content=Content("text/html", html_content),
            )

            if plain_content:
                message.content = [
                    Content("text/plain", plain_content),
                    Content("text/html", html_content),
                ]

            response = self.client.send(message)

            if response.status_code in [200, 201, 202]:
                logger.info(f"Successfully sent email to {to_email}")
                return {
                    "success": True,
                    "message_id": response.headers.get("X-Message-Id"),
                    "provider": "sendgrid",
                    "status_code": response.status_code,
                }
            else:
                logger.error(
                    f"SendGrid error: {response.status_code} - {response.body}"
                )
                return {
                    "success": False,
                    "error": f"Status {response.status_code}",
                }

        except Exception as e:
            logger.error(f"Failed to send email via SendGrid: {e}")
            return {"success": False, "error": str(e)}

    async def send_bulk_email(
        self,
        recipients: List[str],
        subject: str,
        html_content: str,
        plain_content: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Send email to multiple recipients.

        Args:
            recipients: List of recipient email addresses
            subject: Email subject
            html_content: HTML email content
            plain_content: Plain text email content (optional)

        Returns:
            Dict with success status and results
        """
        if not self.enabled or not self.client:
            return {
                "success": False,
                "error": "Email service not configured",
                "sent": 0,
                "failed": len(recipients),
            }

        results = {
            "success": True,
            "sent": 0,
            "failed": 0,
            "failed_emails": [],
        }

        for email in recipients:
            result = await self.send_email(email, subject, html_content, plain_content)
            if result["success"]:
                results["sent"] += 1
            else:
                results["failed"] += 1
                results["failed_emails"].append(email)

        logger.info(
            f"Bulk email results: {results['sent']} sent, {results['failed']} failed"
        )
        return results

    async def send_template_email(
        self,
        to_email: str,
        template_id: str,
        dynamic_data: Dict[str, Any],
    ) -> Dict[str, Any]:
        """
        Send email using SendGrid template.

        Args:
            to_email: Recipient email address
            template_id: SendGrid template ID
            dynamic_data: Template substitution data

        Returns:
            Dict with success status and message_id or error
        """
        if not self.enabled or not self.client:
            return {"success": False, "error": "Email service not configured"}

        try:
            message = Mail(
                from_email=Email(self.from_email, self.from_name),
                to_emails=To(to_email),
            )
            message.template_id = template_id
            message.dynamic_template_data = dynamic_data

            response = self.client.send(message)

            if response.status_code in [200, 201, 202]:
                logger.info(f"Successfully sent template email to {to_email}")
                return {
                    "success": True,
                    "message_id": response.headers.get("X-Message-Id"),
                    "provider": "sendgrid",
                }
            else:
                logger.error(
                    f"SendGrid template error: {response.status_code}"
                )
                return {
                    "success": False,
                    "error": f"Status {response.status_code}",
                }

        except Exception as e:
            logger.error(f"Failed to send template email: {e}")
            return {"success": False, "error": str(e)}

"""Termii SMS service for sending SMS notifications."""
import logging
from typing import Dict, Any, Optional
import httpx
from app.config import settings

logger = logging.getLogger(__name__)


class TermiiService:
    """Termii SMS service."""

    def __init__(self) -> None:
        """Initialize Termii service."""
        self.api_key = settings.termii_api_key
        self.sender_id = settings.termii_sender_id
        self.api_url = settings.termii_api_url
        self.enabled = settings.termii_enabled
        self.client = httpx.AsyncClient(timeout=30.0)

    async def send_sms(
        self,
        phone_number: str,
        message: str,
    ) -> Dict[str, Any]:
        """
        Send SMS via Termii.

        Args:
            phone_number: Recipient phone number (international format)
            message: SMS message content

        Returns:
            Dict with success status and message_id or error
        """
        if not self.enabled:
            logger.warning("Termii SMS not enabled, skipping SMS")
            return {"success": False, "error": "SMS service not enabled"}

        if not self.api_key:
            logger.error("Termii API key not configured")
            return {"success": False, "error": "SMS service not configured"}

        # Clean phone number (remove spaces, hyphens)
        phone_number = phone_number.replace(" ", "").replace("-", "")

        # Ensure phone starts with country code
        if not phone_number.startswith("+"):
            logger.warning(f"Phone number missing country code: {phone_number}")
            # Assume Nigerian number if no country code
            if not phone_number.startswith("234"):
                phone_number = "234" + phone_number.lstrip("0")
        else:
            phone_number = phone_number[1:]  # Remove + for Termii API

        try:
            payload = {
                "to": phone_number,
                "from": self.sender_id,
                "sms": message,
                "type": "plain",
                "channel": "generic",
                "api_key": self.api_key,
            }

            response = await self.client.post(
                f"{self.api_url}/sms/send",
                json=payload,
            )

            response.raise_for_status()
            data = response.json()

            if data.get("message") == "Successfully Sent":
                logger.info(f"Successfully sent SMS via Termii: {data.get('message_id')}")
                return {
                    "success": True,
                    "message_id": data.get("message_id"),
                    "provider": "termii",
                    "balance": data.get("balance"),
                }
            else:
                logger.error(f"Termii SMS failed: {data}")
                return {
                    "success": False,
                    "error": data.get("message", "Unknown error"),
                }

        except httpx.HTTPStatusError as e:
            logger.error(f"Termii HTTP error: {e.response.status_code} - {e.response.text}")
            return {
                "success": False,
                "error": f"HTTP {e.response.status_code}: {e.response.text}",
            }

        except httpx.RequestError as e:
            logger.error(f"Termii request error: {e}")
            return {"success": False, "error": f"Request failed: {str(e)}"}

        except Exception as e:
            logger.error(f"Failed to send SMS via Termii: {e}")
            return {"success": False, "error": str(e)}

    async def send_bulk_sms(
        self,
        phone_numbers: list[str],
        message: str,
    ) -> Dict[str, Any]:
        """
        Send bulk SMS via Termii.

        Args:
            phone_numbers: List of recipient phone numbers
            message: SMS message content

        Returns:
            Dict with success status and results
        """
        if not self.enabled:
            logger.warning("Termii SMS not enabled, skipping bulk SMS")
            return {
                "success": False,
                "error": "SMS service not enabled",
                "sent": 0,
                "failed": len(phone_numbers),
            }

        results = {
            "success": True,
            "sent": 0,
            "failed": 0,
            "failed_numbers": [],
        }

        for phone_number in phone_numbers:
            result = await self.send_sms(phone_number, message)
            if result["success"]:
                results["sent"] += 1
            else:
                results["failed"] += 1
                results["failed_numbers"].append(phone_number)

        logger.info(f"Bulk SMS results: {results['sent']} sent, {results['failed']} failed")
        return results

    async def get_sender_id_status(self) -> Dict[str, Any]:
        """
        Get sender ID status from Termii.

        Returns:
            Dict with sender ID details
        """
        if not self.enabled or not self.api_key:
            return {"success": False, "error": "Termii not configured"}

        try:
            response = await self.client.get(
                f"{self.api_url}/sender-id",
                params={"api_key": self.api_key},
            )

            response.raise_for_status()
            data = response.json()

            return {"success": True, "data": data}

        except Exception as e:
            logger.error(f"Failed to get sender ID status: {e}")
            return {"success": False, "error": str(e)}

    async def close(self) -> None:
        """Close HTTP client."""
        await self.client.aclose()

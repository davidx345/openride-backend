"""Firebase Cloud Messaging service for push notifications."""
import logging
from typing import List, Dict, Any, Optional
from uuid import UUID
import firebase_admin
from firebase_admin import credentials, messaging
from app.config import settings

logger = logging.getLogger(__name__)


class FCMService:
    """Firebase Cloud Messaging service."""

    def __init__(self) -> None:
        """Initialize FCM service."""
        self.initialized = False
        if settings.fcm_enabled:
            try:
                self._initialize_firebase()
                self.initialized = True
                logger.info("FCM service initialized successfully")
            except Exception as e:
                logger.error(f"Failed to initialize FCM: {e}")
                self.initialized = False

    def _initialize_firebase(self) -> None:
        """Initialize Firebase Admin SDK."""
        if not firebase_admin._apps:
            cred = credentials.Certificate(settings.firebase_credentials_path)
            firebase_admin.initialize_app(cred)

    async def send_push_notification(
        self,
        token: str,
        title: str,
        body: str,
        data: Optional[Dict[str, str]] = None,
    ) -> Dict[str, Any]:
        """
        Send push notification to a single device.

        Args:
            token: FCM device token
            title: Notification title
            body: Notification body
            data: Additional data payload

        Returns:
            Dict with success status and message_id or error
        """
        if not self.initialized:
            logger.warning("FCM not initialized, skipping notification")
            return {"success": False, "error": "FCM not initialized"}

        try:
            message = messaging.Message(
                notification=messaging.Notification(
                    title=title,
                    body=body,
                ),
                data=data or {},
                token=token,
                android=messaging.AndroidConfig(
                    priority="high",
                    notification=messaging.AndroidNotification(
                        sound="default",
                        priority="high",
                    ),
                ),
                apns=messaging.APNSConfig(
                    payload=messaging.APNSPayload(
                        aps=messaging.Aps(
                            sound="default",
                            badge=1,
                        ),
                    ),
                ),
            )

            message_id = messaging.send(message)
            logger.info(f"Successfully sent FCM message: {message_id}")

            return {
                "success": True,
                "message_id": message_id,
                "provider": "fcm",
            }

        except messaging.UnregisteredError:
            logger.warning(f"FCM token is unregistered: {token[:20]}...")
            return {
                "success": False,
                "error": "Token unregistered",
                "should_remove_token": True,
            }

        except messaging.SenderIdMismatchError:
            logger.error(f"Sender ID mismatch for token: {token[:20]}...")
            return {
                "success": False,
                "error": "Sender ID mismatch",
                "should_remove_token": True,
            }

        except Exception as e:
            logger.error(f"Failed to send FCM notification: {e}")
            return {"success": False, "error": str(e)}

    async def send_multicast(
        self,
        tokens: List[str],
        title: str,
        body: str,
        data: Optional[Dict[str, str]] = None,
    ) -> Dict[str, Any]:
        """
        Send push notification to multiple devices.

        Args:
            tokens: List of FCM device tokens (max 500)
            title: Notification title
            body: Notification body
            data: Additional data payload

        Returns:
            Dict with success count, failure count, and failed tokens
        """
        if not self.initialized:
            logger.warning("FCM not initialized, skipping notifications")
            return {
                "success": False,
                "error": "FCM not initialized",
                "success_count": 0,
                "failure_count": len(tokens),
            }

        if len(tokens) > 500:
            logger.warning(f"Token list too large ({len(tokens)}), limiting to 500")
            tokens = tokens[:500]

        try:
            message = messaging.MulticastMessage(
                notification=messaging.Notification(
                    title=title,
                    body=body,
                ),
                data=data or {},
                tokens=tokens,
                android=messaging.AndroidConfig(
                    priority="high",
                    notification=messaging.AndroidNotification(
                        sound="default",
                        priority="high",
                    ),
                ),
                apns=messaging.APNSConfig(
                    payload=messaging.APNSPayload(
                        aps=messaging.Aps(
                            sound="default",
                            badge=1,
                        ),
                    ),
                ),
            )

            response = messaging.send_multicast(message)

            failed_tokens = []
            for idx, resp in enumerate(response.responses):
                if not resp.success:
                    failed_tokens.append(tokens[idx])
                    logger.warning(
                        f"Failed to send to token {idx}: {resp.exception}"
                    )

            logger.info(
                f"FCM multicast: {response.success_count} success, "
                f"{response.failure_count} failures"
            )

            return {
                "success": True,
                "success_count": response.success_count,
                "failure_count": response.failure_count,
                "failed_tokens": failed_tokens,
                "provider": "fcm",
            }

        except Exception as e:
            logger.error(f"Failed to send FCM multicast: {e}")
            return {
                "success": False,
                "error": str(e),
                "success_count": 0,
                "failure_count": len(tokens),
            }

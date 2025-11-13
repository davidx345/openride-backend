"""Kafka consumer service for event ingestion."""

import json
import uuid
from datetime import datetime
from typing import Any, Dict, Optional

from confluent_kafka import Consumer, KafkaError, KafkaException, Message
from pydantic import ValidationError

from app.core.clickhouse import clickhouse_manager
from app.core.config import settings
from app.core.logging import get_logger
from app.schemas import (
    BookingCancelledEvent,
    BookingCompletedEvent,
    BookingConfirmedEvent,
    BookingCreatedEvent,
    DriverLocationUpdatedEvent,
    PaymentFailedEvent,
    PaymentInitiatedEvent,
    PaymentRefundedEvent,
    PaymentSuccessEvent,
    RouteActivatedEvent,
    RouteCancelledEvent,
    RouteCreatedEvent,
    RouteUpdatedEvent,
    TripCancelledEvent,
    TripCompletedEvent,
    TripStartedEvent,
    UserKYCVerifiedEvent,
    UserRegisteredEvent,
    UserUpgradedToDriverEvent,
)

logger = get_logger(__name__)

# Event schema mapping
EVENT_SCHEMAS = {
    # User events
    "user.registered": UserRegisteredEvent,
    "user.kyc_verified": UserKYCVerifiedEvent,
    "user.upgraded_to_driver": UserUpgradedToDriverEvent,
    # Booking events
    "booking.created": BookingCreatedEvent,
    "booking.confirmed": BookingConfirmedEvent,
    "booking.cancelled": BookingCancelledEvent,
    "booking.completed": BookingCompletedEvent,
    # Payment events
    "payment.initiated": PaymentInitiatedEvent,
    "payment.success": PaymentSuccessEvent,
    "payment.failed": PaymentFailedEvent,
    "payment.refunded": PaymentRefundedEvent,
    # Trip events
    "trip.started": TripStartedEvent,
    "trip.completed": TripCompletedEvent,
    "trip.cancelled": TripCancelledEvent,
    # Route events
    "route.created": RouteCreatedEvent,
    "route.activated": RouteActivatedEvent,
    "route.cancelled": RouteCancelledEvent,
    "route.updated": RouteUpdatedEvent,
    # Location events
    "driver.location.updated": DriverLocationUpdatedEvent,
}


class KafkaConsumerService:
    """Kafka consumer for event processing."""

    def __init__(self) -> None:
        """Initialize Kafka consumer."""
        self.consumer: Optional[Consumer] = None
        self.running = False
        self.batch: list[Dict[str, Any]] = []

    def connect(self) -> None:
        """Initialize Kafka consumer connection."""
        try:
            consumer_config = {
                "bootstrap.servers": settings.KAFKA_BOOTSTRAP_SERVERS,
                "group.id": settings.KAFKA_CONSUMER_GROUP_ID,
                "auto.offset.reset": settings.KAFKA_AUTO_OFFSET_RESET,
                "enable.auto.commit": settings.KAFKA_ENABLE_AUTO_COMMIT,
                "max.poll.records": settings.KAFKA_MAX_POLL_RECORDS,
                "session.timeout.ms": settings.KAFKA_SESSION_TIMEOUT_MS,
                "request.timeout.ms": settings.KAFKA_REQUEST_TIMEOUT_MS,
                "client.id": f"{settings.APP_NAME}-{uuid.uuid4().hex[:8]}",
            }

            self.consumer = Consumer(consumer_config)
            self.consumer.subscribe(settings.kafka_topics)
            
            logger.info(
                "kafka_consumer_connected",
                topics=settings.kafka_topics,
                group_id=settings.KAFKA_CONSUMER_GROUP_ID,
            )
        except Exception as e:
            logger.error("kafka_consumer_connection_failed", error=str(e))
            raise

    def disconnect(self) -> None:
        """Close Kafka consumer connection."""
        if self.consumer:
            self.running = False
            self.consumer.close()
            logger.info("kafka_consumer_disconnected")

    def validate_event(self, event_type: str, event_data: Dict[str, Any]) -> Optional[Any]:
        """Validate event against Pydantic schema.
        
        Args:
            event_type: Event type string
            event_data: Raw event data
            
        Returns:
            Validated event object or None if validation fails
        """
        schema_class = EVENT_SCHEMAS.get(event_type)
        if not schema_class:
            logger.warning("unknown_event_type", event_type=event_type)
            return None

        try:
            return schema_class(**event_data)
        except ValidationError as e:
            logger.error("event_validation_failed", event_type=event_type, errors=e.errors())
            return None

    def insert_to_clickhouse(self, event: Any, metadata: Dict[str, Any]) -> None:
        """Insert validated event to ClickHouse.
        
        Args:
            event: Validated event object
            metadata: Kafka metadata (partition, offset, timestamp)
        """
        try:
            # Prepare row for events_raw table
            event_dict = event.model_dump() if hasattr(event, "model_dump") else event.dict()
            
            row = [
                str(uuid.uuid4()),  # event_id
                event.event_type,
                event.event_timestamp,
                event_dict.get("user_id") or event_dict.get("booking_id") or event_dict.get("trip_id") or event_dict.get("route_id") or "",
                self._get_entity_type(event.event_type),
                event_dict.get("user_id"),
                json.dumps(event_dict),  # metadata as JSON
                metadata["partition"],
                metadata["offset"],
                metadata["timestamp"],
                datetime.utcnow(),  # ingested_at
            ]
            
            self.batch.append(row)
            
            # Batch insert when batch size reached
            if len(self.batch) >= settings.AGGREGATION_BATCH_SIZE:
                self._flush_batch()
                
        except Exception as e:
            logger.error("clickhouse_insert_failed", event_type=event.event_type, error=str(e))

    def _get_entity_type(self, event_type: str) -> str:
        """Extract entity type from event type.
        
        Args:
            event_type: Event type (e.g., 'user.registered')
            
        Returns:
            Entity type (e.g., 'user')
        """
        return event_type.split(".")[0]

    def _flush_batch(self) -> None:
        """Flush current batch to ClickHouse."""
        if not self.batch:
            return

        try:
            column_names = [
                "event_id", "event_type", "event_timestamp", "entity_id", "entity_type",
                "user_id", "metadata", "kafka_partition", "kafka_offset", "kafka_timestamp",
                "ingested_at"
            ]
            
            clickhouse_manager.insert(
                "openride_analytics.events_raw",
                self.batch,
                column_names=column_names
            )
            
            logger.info("batch_inserted_to_clickhouse", batch_size=len(self.batch))
            self.batch = []
            
        except Exception as e:
            logger.error("batch_insert_failed", batch_size=len(self.batch), error=str(e))
            # Send to DLQ
            self._send_to_dlq(self.batch, str(e))
            self.batch = []

    def _send_to_dlq(self, events: list, error: str) -> None:
        """Send failed events to Dead Letter Queue.
        
        Args:
            events: Failed events
            error: Error message
        """
        # TODO: Implement DLQ logic (send to Kafka DLQ topic or store in database)
        logger.warning("events_sent_to_dlq", count=len(events), error=error)

    def process_message(self, msg: Message) -> None:
        """Process a single Kafka message.
        
        Args:
            msg: Kafka message
        """
        try:
            # Parse message
            value = msg.value().decode("utf-8") if msg.value() else "{}"
            event_data = json.loads(value)
            
            # Extract event type
            event_type = event_data.get("event_type")
            if not event_type:
                logger.warning("missing_event_type", topic=msg.topic())
                return

            # Validate event
            validated_event = self.validate_event(event_type, event_data)
            if not validated_event:
                return

            # Prepare metadata
            metadata = {
                "partition": msg.partition(),
                "offset": msg.offset(),
                "timestamp": datetime.fromtimestamp(msg.timestamp()[1] / 1000),  # Convert from ms
            }

            # Insert to ClickHouse
            self.insert_to_clickhouse(validated_event, metadata)
            
            logger.debug(
                "event_processed",
                event_type=event_type,
                partition=metadata["partition"],
                offset=metadata["offset"],
            )

        except json.JSONDecodeError as e:
            logger.error("json_decode_failed", error=str(e), topic=msg.topic())
        except Exception as e:
            logger.error("message_processing_failed", error=str(e), topic=msg.topic())

    def consume(self) -> None:
        """Start consuming messages from Kafka.
        
        This is a blocking operation that runs until interrupted.
        """
        if not self.consumer:
            raise RuntimeError("Consumer not connected. Call connect() first.")

        self.running = True
        logger.info("kafka_consumer_started")

        try:
            while self.running:
                msg = self.consumer.poll(timeout=1.0)
                
                if msg is None:
                    continue

                if msg.error():
                    if msg.error().code() == KafkaError._PARTITION_EOF:
                        logger.debug("partition_eof", partition=msg.partition())
                    else:
                        logger.error("kafka_error", error=msg.error())
                        raise KafkaException(msg.error())
                else:
                    self.process_message(msg)

                # Manual commit after processing
                if not settings.KAFKA_ENABLE_AUTO_COMMIT:
                    self.consumer.commit(asynchronous=False)

        except KeyboardInterrupt:
            logger.info("kafka_consumer_interrupted")
        finally:
            # Flush remaining batch
            self._flush_batch()
            self.disconnect()


# Global consumer instance
consumer_service = KafkaConsumerService()


def start_consumer() -> None:
    """Start the Kafka consumer service.
    
    Usage:
        if __name__ == "__main__":
            start_consumer()
    """
    try:
        # Connect to ClickHouse
        clickhouse_manager.connect()
        
        # Connect to Kafka
        consumer_service.connect()
        
        # Start consuming
        consumer_service.consume()
        
    except Exception as e:
        logger.error("consumer_startup_failed", error=str(e))
        raise
    finally:
        clickhouse_manager.disconnect()

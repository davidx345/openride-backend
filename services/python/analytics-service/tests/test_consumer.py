"""Test Kafka consumer functionality."""

import json
import pytest
from datetime import datetime
from unittest.mock import Mock, patch, MagicMock

from app.consumer.kafka_consumer import KafkaConsumerService, EVENT_SCHEMAS
from app.schemas.user_events import UserRegisteredEvent


@pytest.fixture
def consumer_service():
    """Create consumer service instance."""
    return KafkaConsumerService()


@pytest.fixture
def sample_user_registered_event():
    """Sample user registered event."""
    return {
        "event_type": "user.registered",
        "event_timestamp": datetime.utcnow().isoformat(),
        "user_id": "user-123",
        "phone": "+2348012345678",
        "role": "RIDER",
        "city": "Lagos",
        "state": "Lagos",
    }


class TestKafkaConsumer:
    """Test Kafka consumer service."""

    def test_connect_success(self, consumer_service):
        """Test successful Kafka connection."""
        with patch('app.consumer.kafka_consumer.Consumer') as mock_consumer_class:
            mock_consumer = Mock()
            mock_consumer_class.return_value = mock_consumer
            
            consumer_service.connect()
            
            assert consumer_service.consumer is not None
            mock_consumer.subscribe.assert_called_once()

    def test_disconnect(self, consumer_service):
        """Test Kafka disconnection."""
        consumer_service.consumer = Mock()
        
        consumer_service.disconnect()
        
        consumer_service.consumer.close.assert_called_once()
        assert consumer_service.running is False

    def test_validate_event_success(self, consumer_service, sample_user_registered_event):
        """Test successful event validation."""
        result = consumer_service.validate_event(
            "user.registered",
            sample_user_registered_event
        )
        
        assert result is not None
        assert isinstance(result, UserRegisteredEvent)
        assert result.user_id == "user-123"
        assert result.phone == "+2348012345678"

    def test_validate_event_unknown_type(self, consumer_service):
        """Test validation with unknown event type."""
        result = consumer_service.validate_event("unknown.event", {})
        
        assert result is None

    def test_validate_event_invalid_data(self, consumer_service):
        """Test validation with invalid event data."""
        invalid_event = {
            "event_type": "user.registered",
            "user_id": "user-123",
            # Missing required fields
        }
        
        result = consumer_service.validate_event("user.registered", invalid_event)
        
        assert result is None

    @patch('app.consumer.kafka_consumer.clickhouse_manager')
    def test_insert_to_clickhouse(self, mock_ch_manager, consumer_service, sample_user_registered_event):
        """Test inserting validated event to ClickHouse."""
        # Arrange
        event = UserRegisteredEvent(**sample_user_registered_event)
        metadata = {
            "partition": 0,
            "offset": 12345,
            "timestamp": datetime.utcnow(),
        }
        
        # Act
        consumer_service.insert_to_clickhouse(event, metadata)
        
        # Assert
        assert len(consumer_service.batch) == 1
        row = consumer_service.batch[0]
        assert row[1] == "user.registered"  # event_type
        assert row[4] == "user"  # entity_type
        assert row[5] == "user-123"  # user_id

    @patch('app.consumer.kafka_consumer.clickhouse_manager')
    def test_flush_batch(self, mock_ch_manager, consumer_service):
        """Test flushing batch to ClickHouse."""
        # Arrange
        consumer_service.batch = [
            ["event-1", "user.registered", datetime.utcnow(), "user-1", "user", "user-1", "{}", 0, 100, datetime.utcnow(), datetime.utcnow()],
            ["event-2", "booking.created", datetime.utcnow(), "booking-1", "booking", "user-2", "{}", 0, 101, datetime.utcnow(), datetime.utcnow()],
        ]
        
        # Act
        consumer_service._flush_batch()
        
        # Assert
        mock_ch_manager.insert.assert_called_once()
        assert len(consumer_service.batch) == 0

    def test_get_entity_type(self, consumer_service):
        """Test extracting entity type from event type."""
        assert consumer_service._get_entity_type("user.registered") == "user"
        assert consumer_service._get_entity_type("booking.created") == "booking"
        assert consumer_service._get_entity_type("payment.success") == "payment"

    @patch('app.consumer.kafka_consumer.clickhouse_manager')
    def test_process_message_success(self, mock_ch_manager, consumer_service, sample_user_registered_event):
        """Test successful message processing."""
        # Arrange
        mock_msg = Mock()
        mock_msg.value.return_value = json.dumps(sample_user_registered_event).encode('utf-8')
        mock_msg.partition.return_value = 0
        mock_msg.offset.return_value = 12345
        mock_msg.timestamp.return_value = (1, int(datetime.utcnow().timestamp() * 1000))
        
        # Act
        consumer_service.process_message(mock_msg)
        
        # Assert
        assert len(consumer_service.batch) == 1

    def test_process_message_invalid_json(self, consumer_service):
        """Test processing message with invalid JSON."""
        # Arrange
        mock_msg = Mock()
        mock_msg.value.return_value = b"invalid json"
        mock_msg.topic.return_value = "test.topic"
        
        # Act
        consumer_service.process_message(mock_msg)
        
        # Assert
        assert len(consumer_service.batch) == 0

    def test_process_message_missing_event_type(self, consumer_service):
        """Test processing message without event_type."""
        # Arrange
        mock_msg = Mock()
        mock_msg.value.return_value = json.dumps({"user_id": "user-123"}).encode('utf-8')
        mock_msg.topic.return_value = "test.topic"
        
        # Act
        consumer_service.process_message(mock_msg)
        
        # Assert
        assert len(consumer_service.batch) == 0


class TestEventSchemas:
    """Test event schema mappings."""

    def test_all_event_types_have_schemas(self):
        """Test that all expected event types have schemas."""
        expected_events = [
            "user.registered",
            "user.kyc_verified",
            "user.upgraded_to_driver",
            "booking.created",
            "booking.confirmed",
            "booking.cancelled",
            "booking.completed",
            "payment.initiated",
            "payment.success",
            "payment.failed",
            "payment.refunded",
            "trip.started",
            "trip.completed",
            "trip.cancelled",
            "route.created",
            "route.activated",
            "route.cancelled",
            "driver.location.updated",
        ]
        
        for event_type in expected_events:
            assert event_type in EVENT_SCHEMAS
            assert EVENT_SCHEMAS[event_type] is not None

"""Test event validation and schema."""

import pytest
from datetime import datetime
from uuid import uuid4
from decimal import Decimal

from app.schemas.user_events import UserRegisteredEvent
from app.schemas.booking_events import BookingCreatedEvent
from app.schemas.payment_events import PaymentSuccessEvent
from app.schemas.trip_events import TripCompletedEvent


def test_user_registered_event_valid():
    """Test user registered event validation."""
    event = UserRegisteredEvent(
        event_timestamp=datetime.utcnow(),
        user_id=uuid4(),
        phone="+2348012345678",
        role="RIDER",
        full_name="John Doe",
        city="Lagos",
        state="Lagos",
    )
    
    assert event.event_type == "user.registered"
    assert event.role == "RIDER"


def test_booking_created_event_valid():
    """Test booking created event validation."""
    event = BookingCreatedEvent(
        event_timestamp=datetime.utcnow(),
        booking_id=uuid4(),
        rider_id=uuid4(),
        driver_id=uuid4(),
        route_id=uuid4(),
        seats_booked=2,
        total_price=Decimal("3000.00"),
        pickup_stop_id=uuid4(),
        dropoff_stop_id=uuid4(),
        pickup_lat=6.5244,
        pickup_lon=3.3792,
        dropoff_lat=6.4281,
        dropoff_lon=3.4219,
        scheduled_departure=datetime.utcnow(),
    )
    
    assert event.event_type == "booking.created"
    assert event.seats_booked == 2


def test_payment_success_event_valid():
    """Test payment success event validation."""
    event = PaymentSuccessEvent(
        event_timestamp=datetime.utcnow(),
        payment_id=uuid4(),
        booking_id=uuid4(),
        rider_id=uuid4(),
        amount=Decimal("3000.00"),
        payment_method="card",
        provider="interswitch",
        provider_transaction_id="ISW_123",
        processing_time_ms=2500,
    )
    
    assert event.event_type == "payment.success"
    assert event.processing_time_ms == 2500


def test_trip_completed_event_valid():
    """Test trip completed event validation."""
    event = TripCompletedEvent(
        event_timestamp=datetime.utcnow(),
        trip_id=uuid4(),
        booking_id=uuid4(),
        driver_id=uuid4(),
        rider_id=uuid4(),
        route_id=uuid4(),
        actual_departure=datetime.utcnow(),
        actual_arrival=datetime.utcnow(),
        duration_minutes=80,
        distance_km=25.5,
        end_lat=6.4281,
        end_lon=3.4219,
        driver_earnings=Decimal("2550.00"),
        platform_commission=Decimal("450.00"),
        on_time=True,
    )
    
    assert event.event_type == "trip.completed"
    assert event.on_time is True

"""Pydantic schemas for payment-related events."""

from datetime import datetime
from decimal import Decimal
from typing import Optional
from uuid import UUID

from pydantic import BaseModel, Field


class PaymentInitiatedEvent(BaseModel):
    """Payment initiated event schema."""

    event_type: str = Field(default="payment.initiated", const=True)
    event_timestamp: datetime
    payment_id: UUID
    booking_id: UUID
    rider_id: UUID
    amount: Decimal = Field(..., decimal_places=2)
    currency: str = "NGN"
    payment_method: str  # 'card', 'bank_transfer', 'ussd', 'wallet'
    provider: str = "interswitch"
    provider_transaction_id: Optional[str] = None
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "payment.initiated",
                "event_timestamp": "2024-11-14T10:02:00Z",
                "payment_id": "p1234567-e89b-12d3-a456-426614174000",
                "booking_id": "b1234567-e89b-12d3-a456-426614174000",
                "rider_id": "r1234567-e89b-12d3-a456-426614174000",
                "amount": "3000.00",
                "currency": "NGN",
                "payment_method": "card",
                "provider": "interswitch",
            }
        }


class PaymentSuccessEvent(BaseModel):
    """Payment success event schema."""

    event_type: str = Field(default="payment.success", const=True)
    event_timestamp: datetime
    payment_id: UUID
    booking_id: UUID
    rider_id: UUID
    amount: Decimal
    currency: str = "NGN"
    payment_method: str
    provider: str
    provider_transaction_id: str
    processing_time_ms: int = Field(..., description="Processing time in milliseconds")
    card_last4: Optional[str] = None
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "payment.success",
                "event_timestamp": "2024-11-14T10:02:30Z",
                "payment_id": "p1234567-e89b-12d3-a456-426614174000",
                "booking_id": "b1234567-e89b-12d3-a456-426614174000",
                "rider_id": "r1234567-e89b-12d3-a456-426614174000",
                "amount": "3000.00",
                "currency": "NGN",
                "payment_method": "card",
                "provider": "interswitch",
                "provider_transaction_id": "ISW_TXN_123456",
                "processing_time_ms": 2500,
                "card_last4": "4321",
            }
        }


class PaymentFailedEvent(BaseModel):
    """Payment failed event schema."""

    event_type: str = Field(default="payment.failed", const=True)
    event_timestamp: datetime
    payment_id: UUID
    booking_id: UUID
    rider_id: UUID
    amount: Decimal
    currency: str = "NGN"
    payment_method: str
    provider: str
    provider_transaction_id: Optional[str] = None
    error_code: str
    error_message: str
    processing_time_ms: Optional[int] = None
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "payment.failed",
                "event_timestamp": "2024-11-14T10:02:45Z",
                "payment_id": "p1234567-e89b-12d3-a456-426614174000",
                "booking_id": "b1234567-e89b-12d3-a456-426614174000",
                "rider_id": "r1234567-e89b-12d3-a456-426614174000",
                "amount": "3000.00",
                "currency": "NGN",
                "payment_method": "card",
                "provider": "interswitch",
                "error_code": "INSUFFICIENT_FUNDS",
                "error_message": "Card has insufficient funds",
                "processing_time_ms": 1500,
            }
        }


class PaymentRefundedEvent(BaseModel):
    """Payment refunded event schema."""

    event_type: str = Field(default="payment.refunded", const=True)
    event_timestamp: datetime
    payment_id: UUID
    booking_id: UUID
    rider_id: UUID
    refund_amount: Decimal
    original_amount: Decimal
    currency: str = "NGN"
    refund_reason: str
    refunded_by: Optional[UUID] = None  # Admin user ID if manual refund
    provider_refund_id: Optional[str] = None
    
    class Config:
        json_schema_extra = {
            "example": {
                "event_type": "payment.refunded",
                "event_timestamp": "2024-11-14T11:00:00Z",
                "payment_id": "p1234567-e89b-12d3-a456-426614174000",
                "booking_id": "b1234567-e89b-12d3-a456-426614174000",
                "rider_id": "r1234567-e89b-12d3-a456-426614174000",
                "refund_amount": "3000.00",
                "original_amount": "3000.00",
                "currency": "NGN",
                "refund_reason": "Booking cancelled by rider",
            }
        }

package com.openride.booking.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event published when a booking is cancelled
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCancelledEvent {

    private UUID bookingId;
    private String bookingReference;
    private UUID riderId;
    private UUID routeId;
    private Integer numberOfSeats;
    private List<String> seatNumbers;
    private BigDecimal totalAmount;
    private BigDecimal refundAmount;
    private String cancellationReason;
    private Instant cancelledAt;
    private String eventId;
    private Instant eventTimestamp;
}

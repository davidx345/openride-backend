package com.openride.booking.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event published when a booking is completed (trip finished)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCompletedEvent {

    private UUID bookingId;
    private String bookingReference;
    private UUID riderId;
    private UUID routeId;
    private BigDecimal totalAmount;
    private Instant completedAt;
    private String eventId;
    private Instant eventTimestamp;
}

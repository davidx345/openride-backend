package com.openride.booking.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Event published when a booking is confirmed (paid)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmedEvent {

    private UUID bookingId;
    private String bookingReference;
    private UUID riderId;
    private UUID routeId;
    private LocalDate travelDate;
    private LocalTime departureTime;
    private String pickupLocationId;
    private String dropoffLocationId;
    private Integer numberOfSeats;
    private List<String> seatNumbers;
    private BigDecimal totalAmount;
    private String paymentId;
    private Instant confirmedAt;
    private String eventId;
    private Instant eventTimestamp;
}

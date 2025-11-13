package com.openride.booking.dto;

import com.openride.booking.model.enums.BookingSource;
import com.openride.booking.model.enums.BookingStatus;
import com.openride.booking.model.enums.PaymentStatus;
import com.openride.booking.model.enums.RefundStatus;
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
 * Response DTO for booking details
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingDTO {

    private UUID id;
    private String bookingReference;
    
    // User references
    private UUID riderId;
    private UUID driverId;
    
    // Route details
    private UUID routeId;
    private String routeName;
    private StopDTO originStop;
    private StopDTO destinationStop;
    private LocalDate travelDate;
    private LocalTime departureTime;
    
    // Seat allocation
    private Integer seatsBooked;
    private List<Integer> seatNumbers;
    
    // Pricing
    private BigDecimal pricePerSeat;
    private BigDecimal totalPrice;
    private BigDecimal platformFee;
    
    // Status
    private BookingStatus status;
    private UUID paymentId;
    private PaymentStatus paymentStatus;
    
    // Cancellation
    private String cancellationReason;
    private Instant cancelledAt;
    private BigDecimal refundAmount;
    private RefundStatus refundStatus;
    
    // Metadata
    private BookingSource bookingSource;
    
    // Timestamps
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private Instant confirmedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StopDTO {
        private UUID id;
        private String name;
        private CoordinatesDTO coordinates;
        private Integer sequenceNumber;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CoordinatesDTO {
        private Double lat;
        private Double lng;
    }
}

package com.openride.booking.model;

import com.openride.booking.exception.InvalidStateTransitionException;
import com.openride.booking.model.enums.BookingSource;
import com.openride.booking.model.enums.BookingStatus;
import com.openride.booking.model.enums.PaymentStatus;
import com.openride.booking.model.enums.RefundStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Booking entity representing a rider's seat reservation
 * 
 * Features:
 * - State machine with validated transitions
 * - Seat inventory tracking
 * - Payment integration
 * - Cancellation and refund support
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_bookings_rider", columnList = "rider_id"),
    @Index(name = "idx_bookings_driver", columnList = "driver_id"),
    @Index(name = "idx_bookings_route", columnList = "route_id"),
    @Index(name = "idx_bookings_route_date", columnList = "route_id, travel_date"),
    @Index(name = "idx_bookings_status", columnList = "status"),
    @Index(name = "idx_bookings_reference", columnList = "booking_reference")
})
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_reference", unique = true, nullable = false, length = 20)
    private String bookingReference;

    // User references
    @Column(name = "rider_id", nullable = false)
    private UUID riderId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    // Route and journey details
    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Column(name = "origin_stop_id", nullable = false)
    private UUID originStopId;

    @Column(name = "destination_stop_id", nullable = false)
    private UUID destinationStopId;

    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Column(name = "departure_time", nullable = false)
    private LocalTime departureTime;

    // Seat allocation
    @Column(name = "seats_booked", nullable = false)
    private Integer seatsBooked;

    @Column(name = "seat_numbers", nullable = false, columnDefinition = "integer[]")
    private List<Integer> seatNumbers = new ArrayList<>();

    // Pricing
    @Column(name = "price_per_seat", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerSeat;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "platform_fee", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal platformFee = BigDecimal.ZERO;

    // Status tracking
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    // Cancellation and refund
    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", length = 20)
    @Builder.Default
    private RefundStatus refundStatus = RefundStatus.NONE;

    // Metadata
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_source", length = 20)
    @Builder.Default
    private BookingSource bookingSource = BookingSource.WEB;

    // Timestamps
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    // Relationships
    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BookingStatusHistory> statusHistory = new ArrayList<>();

    /**
     * Transition to a new status with validation
     * 
     * @param newStatus Target status
     * @param reason Reason for transition
     * @throws InvalidStateTransitionException if transition is not allowed
     */
    public void transitionTo(BookingStatus newStatus, String reason) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new InvalidStateTransitionException(
                String.format("Cannot transition from %s to %s", this.status, newStatus)
            );
        }

        BookingStatus oldStatus = this.status;
        this.status = newStatus;

        // Update timestamps based on status
        if (newStatus == BookingStatus.CONFIRMED) {
            this.confirmedAt = Instant.now();
            this.expiresAt = null; // No longer expires
        } else if (newStatus == BookingStatus.CANCELLED) {
            this.cancelledAt = Instant.now();
        }

        // Add to status history
        BookingStatusHistory historyEntry = BookingStatusHistory.builder()
            .booking(this)
            .fromStatus(oldStatus)
            .toStatus(newStatus)
            .reason(reason)
            .build();
        
        this.statusHistory.add(historyEntry);
    }

    /**
     * Check if booking can be cancelled
     */
    public boolean isCancellable() {
        return this.status.isCancellable();
    }

    /**
     * Check if booking is in a terminal state
     */
    public boolean isTerminal() {
        return this.status.isTerminal();
    }

    /**
     * Check if booking is active
     */
    public boolean isActive() {
        return this.status.isActive();
    }

    /**
     * Check if seats are currently held in Redis
     */
    public boolean hasSeatsHeld() {
        return this.status.hasSeatsHeld();
    }
}

package com.openride.booking.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Seat hold entity (backup for Redis holds)
 * 
 * Used for reconciliation and backup when Redis is unavailable
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "seat_holds", 
       uniqueConstraints = @UniqueConstraint(
           name = "unique_seat_hold", 
           columnNames = {"route_id", "travel_date", "seat_number", "booking_id"}
       ),
       indexes = {
           @Index(name = "idx_seat_holds_route_date", columnList = "route_id, travel_date"),
           @Index(name = "idx_seat_holds_booking", columnList = "booking_id")
       })
public class SeatHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Column(name = "travel_date", nullable = false)
    private LocalDate travelDate;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "held_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant heldAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @PrePersist
    protected void onCreate() {
        if (heldAt == null) {
            heldAt = Instant.now();
        }
    }

    /**
     * Check if hold is still active
     */
    public boolean isActive() {
        return releasedAt == null && Instant.now().isBefore(expiresAt);
    }

    /**
     * Check if hold has expired
     */
    public boolean isExpired() {
        return releasedAt == null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Release the seat hold
     */
    public void release() {
        this.releasedAt = Instant.now();
    }
}

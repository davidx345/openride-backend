package com.openride.ticketing.model.entity;

import com.openride.ticketing.model.enums.TicketStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ticket entity representing a cryptographically signed travel ticket.
 * 
 * Each ticket is:
 * - Linked to a confirmed booking
 * - Cryptographically signed with ECDSA
 * - Encoded as a QR code for scanning
 * - Optionally anchored to blockchain via Merkle tree
 */
@Entity
@Table(name = "tickets", indexes = {
    @Index(name = "idx_tickets_booking_id", columnList = "booking_id"),
    @Index(name = "idx_tickets_rider_id", columnList = "rider_id"),
    @Index(name = "idx_tickets_driver_id", columnList = "driver_id"),
    @Index(name = "idx_tickets_route_id", columnList = "route_id"),
    @Index(name = "idx_tickets_status", columnList = "status"),
    @Index(name = "idx_tickets_trip_date", columnList = "trip_date"),
    @Index(name = "idx_tickets_hash", columnList = "hash"),
    @Index(name = "idx_tickets_merkle_batch_id", columnList = "merkle_batch_id"),
    @Index(name = "idx_tickets_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_id", nullable = false, unique = true)
    private UUID bookingId;

    @Column(name = "rider_id", nullable = false)
    private UUID riderId;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "route_id", nullable = false)
    private UUID routeId;

    @Column(name = "trip_date", nullable = false)
    private LocalDateTime tripDate;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Column(name = "pickup_stop", nullable = false)
    private String pickupStop;

    @Column(name = "dropoff_stop", nullable = false)
    private String dropoffStop;

    @Column(name = "fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal fare;

    // Cryptographic fields
    
    @Column(name = "canonical_json", nullable = false, columnDefinition = "TEXT")
    private String canonicalJson;

    @Column(name = "hash", nullable = false, unique = true, length = 64)
    private String hash;

    @Column(name = "signature", nullable = false, columnDefinition = "TEXT")
    private String signature;

    @Column(name = "qr_code", columnDefinition = "TEXT")
    private String qrCode;

    // Status and batch information
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TicketStatus status = TicketStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merkle_batch_id", foreignKey = @ForeignKey(name = "fk_tickets_merkle_batch"))
    private MerkleBatch merkleBatch;

    // Validity period
    
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Audit fields
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Check if ticket has expired.
     * 
     * @return true if current time is past expiration time
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if ticket is in a valid state for use.
     * 
     * @return true if status is VALID and not expired
     */
    public boolean isValid() {
        return status == TicketStatus.VALID && !isExpired();
    }

    /**
     * Check if ticket has been used.
     * 
     * @return true if status is USED
     */
    public boolean isUsed() {
        return status == TicketStatus.USED;
    }

    /**
     * Check if ticket has been revoked.
     * 
     * @return true if status is REVOKED
     */
    public boolean isRevoked() {
        return status == TicketStatus.REVOKED;
    }

    /**
     * Mark ticket as used.
     */
    public void markAsUsed() {
        this.status = TicketStatus.USED;
    }

    /**
     * Mark ticket as expired.
     */
    public void markAsExpired() {
        this.status = TicketStatus.EXPIRED;
    }

    /**
     * Mark ticket as revoked.
     */
    public void markAsRevoked() {
        this.status = TicketStatus.REVOKED;
    }

    /**
     * Mark ticket as valid.
     */
    public void markAsValid() {
        this.status = TicketStatus.VALID;
    }
}

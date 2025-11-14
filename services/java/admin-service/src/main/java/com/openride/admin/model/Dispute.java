package com.openride.admin.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Dispute entity for booking disputes and support tickets.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "disputes", indexes = {
    @Index(name = "idx_disputes_booking", columnList = "booking_id"),
    @Index(name = "idx_disputes_reporter", columnList = "reporter_id"),
    @Index(name = "idx_disputes_status", columnList = "status"),
    @Index(name = "idx_disputes_type", columnList = "dispute_type"),
    @Index(name = "idx_disputes_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_id", nullable = false)
    private UUID bookingId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Column(name = "reported_id")
    private UUID reportedId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_type", nullable = false, length = 50)
    private DisputeType disputeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private DisputeStatus status = DisputeStatus.OPEN;

    @Column(name = "subject", nullable = false)
    private String subject;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "evidence_urls", columnDefinition = "text[]")
    private String[] evidenceUrls;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum DisputeType {
        PAYMENT,
        BOOKING,
        DRIVER_BEHAVIOR,
        RIDER_BEHAVIOR,
        OTHER
    }

    public enum DisputeStatus {
        OPEN,
        IN_PROGRESS,
        RESOLVED,
        REJECTED
    }
}

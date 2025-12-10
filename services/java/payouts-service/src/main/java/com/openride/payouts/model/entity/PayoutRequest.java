package com.openride.payouts.model.entity;

import com.openride.payouts.model.enums.PayoutStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Payout request entity representing driver's withdrawal request.
 * 
 * Lifecycle: PENDING -> APPROVED/REJECTED -> PROCESSING -> COMPLETED/FAILED
 */
@Entity
@Table(name = "payout_requests", indexes = {
    @Index(name = "idx_payout_requests_driver", columnList = "driver_id"),
    @Index(name = "idx_payout_requests_status", columnList = "status,requested_at"),
    @Index(name = "idx_payout_requests_settlement", columnList = "settlement_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "wallet_id")
    private UUID walletId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id", nullable = false)
    private BankAccount bankAccount;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PayoutStatus status = PayoutStatus.PENDING;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewer_notes", columnDefinition = "TEXT")
    private String reviewerNotes;

    @Column(name = "settlement_id")
    private UUID settlementId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "provider_reference")
    private String providerReference;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Approve payout request.
     * 
     * @param reviewerId Admin user ID
     * @param notes Optional reviewer notes
     */
    public void approve(UUID reviewerId, String notes) {
        if (this.status != PayoutStatus.PENDING) {
            throw new IllegalStateException("Can only approve PENDING payout");
        }
        this.status = PayoutStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewerNotes = notes;
        this.reviewedAt = LocalDateTime.now();
    }

    public void markAsCompleted(String providerReference) {
        this.status = PayoutStatus.COMPLETED;
        this.providerReference = providerReference;
        this.completedAt = LocalDateTime.now();
        this.processedAt = LocalDateTime.now();
    }

    public void markAsFailed(String reason) {
        this.status = PayoutStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = LocalDateTime.now();
        this.processedAt = LocalDateTime.now();
    }

    public UUID getBankAccountId() {
        return bankAccount != null ? bankAccount.getId() : null;
    }

    /**
     * Set bank account by ID. Creates a reference BankAccount with only the ID set.
     * Primarily used for testing purposes.
     * 
     * @param bankAccountId The bank account ID
     */
    public void setBankAccountId(UUID bankAccountId) {
        if (bankAccountId != null) {
            this.bankAccount = BankAccount.builder().id(bankAccountId).build();
        } else {
            this.bankAccount = null;
        }
    }

    public String getReviewNotes() {
        return reviewerNotes;
    }

    /**
     * Set review notes (alias for reviewerNotes for API consistency).
     * 
     * @param notes The review notes
     */
    public void setReviewNotes(String notes) {
        this.reviewerNotes = notes;
    }

    /**
     * Reject payout request.
     * 
     * @param reviewerId Admin user ID
     * @param notes Required rejection reason
     */
    public void reject(UUID reviewerId, String notes) {
        if (this.status != PayoutStatus.PENDING) {
            throw new IllegalStateException("Can only reject PENDING payout");
        }
        if (notes == null || notes.trim().isEmpty()) {
            throw new IllegalArgumentException("Rejection notes are required");
        }
        this.status = PayoutStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewerNotes = notes;
        this.reviewedAt = LocalDateTime.now();
    }

    /**
     * Mark as processing.
     * 
     * @param settlementId Settlement batch ID
     */
    public void markAsProcessing(UUID settlementId) {
        if (this.status != PayoutStatus.APPROVED) {
            throw new IllegalStateException("Can only process APPROVED payout");
        }
        this.status = PayoutStatus.PROCESSING;
        this.settlementId = settlementId;
    }

    /**
     * Mark as completed.
     */
    public void markAsCompleted() {
        if (this.status != PayoutStatus.PROCESSING) {
            throw new IllegalStateException("Can only complete PROCESSING payout");
        }
        this.status = PayoutStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Check if payout is in final state.
     * 
     * @return true if completed, rejected, or failed
     */
    public boolean isFinalState() {
        return this.status == PayoutStatus.COMPLETED ||
               this.status == PayoutStatus.REJECTED ||
               this.status == PayoutStatus.FAILED;
    }

    /**
     * Check if payout can be cancelled.
     * 
     * @return true if pending or approved
     */
    public boolean isCancellable() {
        return this.status == PayoutStatus.PENDING ||
               this.status == PayoutStatus.APPROVED;
    }
}

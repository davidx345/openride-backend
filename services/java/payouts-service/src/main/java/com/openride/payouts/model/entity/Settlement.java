package com.openride.payouts.model.entity;

import com.openride.payouts.model.enums.SettlementStatus;
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
 * Settlement entity representing batch processing of approved payouts.
 */
@Entity
@Table(name = "settlements", indexes = {
    @Index(name = "idx_settlements_status", columnList = "status,initiated_at"),
    @Index(name = "idx_settlements_batch_ref", columnList = "batch_reference")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "batch_reference", nullable = false, unique = true, length = 100)
    private String batchReference;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "payout_count", nullable = false)
    private Integer payoutCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SettlementStatus status = SettlementStatus.PENDING;

    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Column(name = "initiated_at", nullable = false)
    @Builder.Default
    private LocalDateTime initiatedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "provider_reference")
    private String providerReference;

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
     * Generate batch reference in format: SETTLEMENT-YYYYMMDD-HHMMSS-UUID
     * 
     * @return Generated batch reference
     */
    public static String generateBatchReference() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = String.format("%04d%02d%02d-%02d%02d%02d",
                now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                now.getHour(), now.getMinute(), now.getSecond());
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("SETTLEMENT-%s-%s", timestamp, shortUuid);
    }

    /**
     * Mark settlement as processing.
     * 
     * @param provider Payment provider name
     */
    public void markAsProcessing(String provider) {
        if (this.status != SettlementStatus.PENDING) {
            throw new IllegalStateException("Can only process PENDING settlement");
        }
        this.status = SettlementStatus.PROCESSING;
        this.provider = provider;
    }

    /**
     * Mark settlement as completed.
     * 
     * @param providerReference Provider's reference/transaction ID
     */
    public void markAsCompleted(String providerReference) {
        if (this.status != SettlementStatus.PROCESSING) {
            throw new IllegalStateException("Can only complete PROCESSING settlement");
        }
        this.status = SettlementStatus.COMPLETED;
        this.providerReference = providerReference;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Mark settlement as failed.
     * 
     * @param reason Failure reason
     */
    public void markAsFailed(String reason) {
        if (this.status != SettlementStatus.PROCESSING) {
            throw new IllegalStateException("Can only fail PROCESSING settlement");
        }
        this.status = SettlementStatus.FAILED;
        this.failureReason = reason;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Check if settlement is in final state.
     * 
     * @return true if completed or failed
     */
    public boolean isFinalState() {
        return this.status == SettlementStatus.COMPLETED ||
               this.status == SettlementStatus.FAILED;
    }
}

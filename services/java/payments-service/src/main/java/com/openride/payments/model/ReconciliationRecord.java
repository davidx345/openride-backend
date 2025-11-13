package com.openride.payments.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a payment reconciliation record.
 * Stores results of daily reconciliation between local and Korapay records.
 */
@Entity
@Table(name = "reconciliation_records", indexes = {
    @Index(name = "idx_reconciliation_date", columnList = "reconciliation_date"),
    @Index(name = "idx_reconciliation_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reconciliation_date", nullable = false)
    private LocalDate reconciliationDate;

    @Column(name = "total_local_payments", nullable = false)
    private Integer totalLocalPayments;

    @Column(name = "total_korapay_payments", nullable = false)
    private Integer totalKorapayPayments;

    @Column(name = "total_local_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalLocalAmount;

    @Column(name = "total_korapay_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalKorapayAmount;

    @Column(name = "discrepancy_count", nullable = false)
    private Integer discrepancyCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconciliationStatus status;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "discrepancies", columnDefinition = "TEXT")
    private String discrepancies; // JSON array of discrepancy details

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Reconciliation status enum.
     */
    public enum ReconciliationStatus {
        MATCHED,       // All payments matched
        DISCREPANCY,   // Found discrepancies
        FAILED         // Reconciliation process failed
    }
}

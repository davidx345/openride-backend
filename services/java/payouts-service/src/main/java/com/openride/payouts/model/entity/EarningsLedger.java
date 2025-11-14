package com.openride.payouts.model.entity;

import com.openride.payouts.model.enums.LedgerEntryType;
import com.openride.payouts.model.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Ledger entry for double-entry accounting of all financial transactions.
 * Immutable once created.
 */
@Entity
@Table(name = "earnings_ledger", indexes = {
    @Index(name = "idx_ledger_driver", columnList = "driver_id,created_at"),
    @Index(name = "idx_ledger_reference", columnList = "reference_id,reference_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarningsLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private LedgerEntryType entryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Create a CREDIT entry for driver earnings.
     * 
     * @param driverId Driver ID
     * @param amount Earning amount
     * @param balanceAfter Balance after this entry
     * @param tripId Trip reference ID
     * @param description Entry description
     * @return Ledger entry
     */
    public static EarningsLedger createEarningEntry(
            UUID driverId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            UUID tripId,
            String description) {
        return EarningsLedger.builder()
                .driverId(driverId)
                .entryType(LedgerEntryType.CREDIT)
                .transactionType(TransactionType.EARNING)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .referenceId(tripId)
                .referenceType("TRIP")
                .description(description)
                .build();
    }

    /**
     * Create a DEBIT entry for payout.
     * 
     * @param driverId Driver ID
     * @param amount Payout amount
     * @param balanceAfter Balance after this entry
     * @param payoutId Payout reference ID
     * @param description Entry description
     * @return Ledger entry
     */
    public static EarningsLedger createPayoutEntry(
            UUID driverId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            UUID payoutId,
            String description) {
        return EarningsLedger.builder()
                .driverId(driverId)
                .entryType(LedgerEntryType.DEBIT)
                .transactionType(TransactionType.PAYOUT)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .referenceId(payoutId)
                .referenceType("PAYOUT")
                .description(description)
                .build();
    }

    /**
     * Create a CREDIT entry for refund.
     * 
     * @param driverId Driver ID
     * @param amount Refund amount
     * @param balanceAfter Balance after this entry
     * @param payoutId Original payout ID
     * @param description Entry description
     * @return Ledger entry
     */
    public static EarningsLedger createRefundEntry(
            UUID driverId,
            BigDecimal amount,
            BigDecimal balanceAfter,
            UUID payoutId,
            String description) {
        return EarningsLedger.builder()
                .driverId(driverId)
                .entryType(LedgerEntryType.CREDIT)
                .transactionType(TransactionType.REFUND)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .referenceId(payoutId)
                .referenceType("PAYOUT")
                .description(description)
                .build();
    }
}

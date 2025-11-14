package com.openride.payouts.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Driver wallet entity for tracking balances and earnings.
 * Implements double-entry accounting principles.
 */
@Entity
@Table(name = "driver_wallets", indexes = {
    @Index(name = "idx_wallet_driver", columnList = "driver_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "driver_id", nullable = false, unique = true)
    private UUID driverId;

    @Column(name = "available_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Column(name = "pending_payout", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal pendingPayout = BigDecimal.ZERO;

    @Column(name = "total_earnings", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalEarnings = BigDecimal.ZERO;

    @Column(name = "total_paid_out", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalPaidOut = BigDecimal.ZERO;

    @Column(name = "last_payout_at")
    private LocalDateTime lastPayoutAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Credit wallet with earnings.
     * 
     * @param amount Amount to credit
     */
    public void creditEarnings(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.availableBalance = this.availableBalance.add(amount);
        this.totalEarnings = this.totalEarnings.add(amount);
    }

    /**
     * Reserve amount for pending payout.
     * 
     * @param amount Amount to reserve
     */
    public void reserveForPayout(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Reserve amount must be positive");
        }
        if (this.availableBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance for payout");
        }
        this.availableBalance = this.availableBalance.subtract(amount);
        this.pendingPayout = this.pendingPayout.add(amount);
    }

    /**
     * Release reserved amount (payout cancelled/rejected).
     * 
     * @param amount Amount to release
     */
    public void releaseReservedAmount(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Release amount must be positive");
        }
        if (this.pendingPayout.compareTo(amount) < 0) {
            throw new IllegalStateException("Cannot release more than pending amount");
        }
        this.pendingPayout = this.pendingPayout.subtract(amount);
        this.availableBalance = this.availableBalance.add(amount);
    }

    /**
     * Complete payout (debit from pending).
     * 
     * @param amount Amount paid out
     */
    public void completePayout(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payout amount must be positive");
        }
        if (this.pendingPayout.compareTo(amount) < 0) {
            throw new IllegalStateException("Cannot payout more than pending amount");
        }
        this.pendingPayout = this.pendingPayout.subtract(amount);
        this.totalPaidOut = this.totalPaidOut.add(amount);
        this.lastPayoutAt = LocalDateTime.now();
    }

    /**
     * Check if wallet has sufficient balance for payout.
     * 
     * @param amount Amount to check
     * @return true if sufficient balance
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return this.availableBalance.compareTo(amount) >= 0;
    }
}

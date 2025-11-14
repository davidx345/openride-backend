package com.openride.payouts.model.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bank account entity for driver payouts.
 * Account must be verified before first payout.
 */
@Entity
@Table(name = "bank_accounts",
    uniqueConstraints = {
        @UniqueConstraint(name = "unique_driver_account",
            columnNames = {"driver_id", "account_number", "bank_code"})
    },
    indexes = {
        @Index(name = "idx_bank_accounts_driver", columnList = "driver_id"),
        @Index(name = "idx_bank_accounts_primary", columnList = "driver_id,is_primary")
    })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "account_number", nullable = false, length = 10)
    @Pattern(regexp = "^\\d{10}$", message = "Account number must be 10 digits")
    private String accountNumber;

    @Column(name = "account_name", nullable = false)
    @Size(max = 255)
    private String accountName;

    @Column(name = "bank_code", nullable = false, length = 10)
    @Pattern(regexp = "^\\d{3}$", message = "Bank code must be 3 digits")
    private String bankCode;

    @Column(name = "bank_name", nullable = false)
    @Size(max = 255)
    private String bankName;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Mark account as verified.
     */
    public void markAsVerified() {
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
    }

    /**
     * Set as primary account.
     * Note: Trigger in database ensures only one primary per driver.
     */
    public void markAsPrimary() {
        this.isPrimary = true;
    }

    /**
     * Remove primary status.
     */
    public void removePrimary() {
        this.isPrimary = false;
    }

    /**
     * Get masked account number for display.
     * 
     * @return Masked account number (e.g., ******1234)
     */
    public String getMaskedAccountNumber() {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "******";
        }
        return "******" + accountNumber.substring(accountNumber.length() - 4);
    }
}

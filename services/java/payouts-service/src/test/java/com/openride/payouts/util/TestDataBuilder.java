package com.openride.payouts.util;

import com.openride.payouts.model.entity.*;
import com.openride.payouts.model.enums.LedgerEntryType;
import com.openride.payouts.model.enums.PayoutStatus;
import com.openride.payouts.model.enums.SettlementStatus;
import com.openride.payouts.model.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test data builder for creating test entities.
 * Provides fluent API for building test objects with sensible defaults.
 */
public class TestDataBuilder {

    /**
     * Builder for DriverWallet entity.
     */
    public static class DriverWalletBuilder {
        private UUID id = UUID.randomUUID();
        private UUID driverId = UUID.randomUUID();
        private BigDecimal availableBalance = BigDecimal.valueOf(50000.00);
        private BigDecimal pendingPayout = BigDecimal.ZERO;
        private BigDecimal totalEarnings = BigDecimal.valueOf(100000.00);
        private BigDecimal totalPaidOut = BigDecimal.valueOf(50000.00);
        private BigDecimal lifetimeEarnings = BigDecimal.valueOf(100000.00);
        private LocalDateTime lastEarningAt = LocalDateTime.now().minusDays(1);
        private LocalDateTime lastPayoutAt = LocalDateTime.now().minusWeeks(1);
        private Long version = 1L;

        public DriverWalletBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public DriverWalletBuilder driverId(UUID driverId) {
            this.driverId = driverId;
            return this;
        }

        public DriverWalletBuilder availableBalance(BigDecimal amount) {
            this.availableBalance = amount;
            return this;
        }

        public DriverWalletBuilder pendingPayout(BigDecimal amount) {
            this.pendingPayout = amount;
            return this;
        }

        public DriverWalletBuilder totalEarnings(BigDecimal amount) {
            this.totalEarnings = amount;
            return this;
        }

        public DriverWalletBuilder totalPaidOut(BigDecimal amount) {
            this.totalPaidOut = amount;
            return this;
        }

        public DriverWalletBuilder lifetimeEarnings(BigDecimal amount) {
            this.lifetimeEarnings = amount;
            return this;
        }

        public DriverWalletBuilder version(Long version) {
            this.version = version;
            return this;
        }

        public DriverWallet build() {
            DriverWallet wallet = new DriverWallet();
            wallet.setId(id);
            wallet.setDriverId(driverId);
            wallet.setAvailableBalance(availableBalance);
            wallet.setPendingPayout(pendingPayout);
            wallet.setTotalEarnings(totalEarnings);
            wallet.setTotalPaidOut(totalPaidOut);
            wallet.setLifetimeEarnings(lifetimeEarnings);
            wallet.setLastEarningAt(lastEarningAt);
            wallet.setLastPayoutAt(lastPayoutAt);
            wallet.setVersion(version);
            wallet.setCreatedAt(LocalDateTime.now().minusMonths(6));
            wallet.setUpdatedAt(LocalDateTime.now());
            return wallet;
        }
    }

    /**
     * Builder for PayoutRequest entity.
     */
    public static class PayoutRequestBuilder {
        private UUID id = UUID.randomUUID();
        private UUID driverId = UUID.randomUUID();
        private UUID walletId = UUID.randomUUID();
        private UUID bankAccountId = UUID.randomUUID();
        private BigDecimal amount = BigDecimal.valueOf(20000.00);
        private PayoutStatus status = PayoutStatus.PENDING;
        private LocalDateTime requestedAt = LocalDateTime.now();
        private UUID reviewedBy = null;
        private String reviewNotes = null;
        private LocalDateTime reviewedAt = null;
        private String providerReference = null;
        private String failureReason = null;
        private LocalDateTime processedAt = null;
        private LocalDateTime completedAt = null;

        public PayoutRequestBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public PayoutRequestBuilder driverId(UUID driverId) {
            this.driverId = driverId;
            return this;
        }

        public PayoutRequestBuilder walletId(UUID walletId) {
            this.walletId = walletId;
            return this;
        }

        public PayoutRequestBuilder bankAccountId(UUID bankAccountId) {
            this.bankAccountId = bankAccountId;
            return this;
        }

        public PayoutRequestBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public PayoutRequestBuilder status(PayoutStatus status) {
            this.status = status;
            return this;
        }

        public PayoutRequestBuilder approved(UUID adminId) {
            this.status = PayoutStatus.APPROVED;
            this.reviewedBy = adminId;
            this.reviewedAt = LocalDateTime.now();
            return this;
        }

        public PayoutRequestBuilder rejected(UUID adminId, String reason) {
            this.status = PayoutStatus.REJECTED;
            this.reviewedBy = adminId;
            this.reviewNotes = reason;
            this.reviewedAt = LocalDateTime.now();
            return this;
        }

        public PayoutRequestBuilder completed(String providerRef) {
            this.status = PayoutStatus.COMPLETED;
            this.providerReference = providerRef;
            this.completedAt = LocalDateTime.now();
            this.processedAt = LocalDateTime.now().minusMinutes(5);
            return this;
        }

        public PayoutRequestBuilder failed(String reason) {
            this.status = PayoutStatus.FAILED;
            this.failureReason = reason;
            this.processedAt = LocalDateTime.now().minusMinutes(5);
            return this;
        }

        public PayoutRequest build() {
            PayoutRequest payout = new PayoutRequest();
            payout.setId(id);
            payout.setDriverId(driverId);
            payout.setWalletId(walletId);
            payout.setBankAccountId(bankAccountId);
            payout.setAmount(amount);
            payout.setStatus(status);
            payout.setRequestedAt(requestedAt);
            payout.setReviewedBy(reviewedBy);
            payout.setReviewNotes(reviewNotes);
            payout.setReviewedAt(reviewedAt);
            payout.setProviderReference(providerReference);
            payout.setFailureReason(failureReason);
            payout.setProcessedAt(processedAt);
            payout.setCompletedAt(completedAt);
            payout.setCreatedAt(LocalDateTime.now());
            payout.setUpdatedAt(LocalDateTime.now());
            return payout;
        }
    }

    /**
     * Builder for BankAccount entity.
     */
    public static class BankAccountBuilder {
        private UUID id = UUID.randomUUID();
        private UUID driverId = UUID.randomUUID();
        private String accountNumber = "0123456789";
        private String bankCode = "058";
        private String bankName = "GTBank";
        private String accountName = "John Doe";
        private Boolean isVerified = true;
        private Boolean isPrimary = false;

        public BankAccountBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public BankAccountBuilder driverId(UUID driverId) {
            this.driverId = driverId;
            return this;
        }

        public BankAccountBuilder accountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
            return this;
        }

        public BankAccountBuilder bankCode(String bankCode) {
            this.bankCode = bankCode;
            return this;
        }

        public BankAccountBuilder bankName(String bankName) {
            this.bankName = bankName;
            return this;
        }

        public BankAccountBuilder accountName(String accountName) {
            this.accountName = accountName;
            return this;
        }

        public BankAccountBuilder verified(Boolean verified) {
            this.isVerified = verified;
            return this;
        }

        public BankAccountBuilder primary(Boolean primary) {
            this.isPrimary = primary;
            return this;
        }

        public BankAccount build() {
            BankAccount account = new BankAccount();
            account.setId(id);
            account.setDriverId(driverId);
            account.setAccountNumber(accountNumber);
            account.setBankCode(bankCode);
            account.setBankName(bankName);
            account.setAccountName(accountName);
            account.setIsVerified(isVerified);
            account.setIsPrimary(isPrimary);
            account.setCreatedAt(LocalDateTime.now());
            account.setUpdatedAt(LocalDateTime.now());
            return account;
        }
    }

    /**
     * Builder for EarningsLedger entity.
     */
    public static class EarningsLedgerBuilder {
        private UUID id = UUID.randomUUID();
        private UUID walletId = UUID.randomUUID();
        private UUID driverId = UUID.randomUUID();
        private BigDecimal amount = BigDecimal.valueOf(8500.00);
        private BigDecimal balanceAfter = BigDecimal.valueOf(50000.00);
        private LedgerEntryType entryType = LedgerEntryType.CREDIT;
        private TransactionType transactionType = TransactionType.EARNING;
        private UUID referenceId = UUID.randomUUID();
        private String referenceType = "TRIP";
        private String description = "Trip earnings";

        public EarningsLedgerBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public EarningsLedgerBuilder walletId(UUID walletId) {
            this.walletId = walletId;
            return this;
        }

        public EarningsLedgerBuilder driverId(UUID driverId) {
            this.driverId = driverId;
            return this;
        }

        public EarningsLedgerBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public EarningsLedgerBuilder balanceAfter(BigDecimal balance) {
            this.balanceAfter = balance;
            return this;
        }

        public EarningsLedgerBuilder credit(TransactionType type) {
            this.entryType = LedgerEntryType.CREDIT;
            this.transactionType = type;
            return this;
        }

        public EarningsLedgerBuilder debit(TransactionType type) {
            this.entryType = LedgerEntryType.DEBIT;
            this.transactionType = type;
            return this;
        }

        public EarningsLedgerBuilder referenceId(UUID referenceId) {
            this.referenceId = referenceId;
            return this;
        }

        public EarningsLedgerBuilder description(String description) {
            this.description = description;
            return this;
        }

        public EarningsLedger build() {
            EarningsLedger ledger = new EarningsLedger();
            ledger.setId(id);
            ledger.setWalletId(walletId);
            ledger.setDriverId(driverId);
            ledger.setAmount(amount);
            ledger.setBalanceAfter(balanceAfter);
            ledger.setEntryType(entryType);
            ledger.setTransactionType(transactionType);
            ledger.setReferenceId(referenceId);
            ledger.setReferenceType(referenceType);
            ledger.setDescription(description);
            ledger.setCreatedAt(LocalDateTime.now());
            return ledger;
        }
    }

    /**
     * Builder for Settlement entity.
     */
    public static class SettlementBuilder {
        private UUID id = UUID.randomUUID();
        private Integer totalPayouts = 10;
        private BigDecimal totalAmount = BigDecimal.valueOf(200000.00);
        private Integer successfulPayouts = 0;
        private Integer failedPayouts = 0;
        private SettlementStatus status = SettlementStatus.PENDING;
        private LocalDateTime completedAt = null;

        public SettlementBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public SettlementBuilder totalPayouts(Integer count) {
            this.totalPayouts = count;
            return this;
        }

        public SettlementBuilder totalAmount(BigDecimal amount) {
            this.totalAmount = amount;
            return this;
        }

        public SettlementBuilder status(SettlementStatus status) {
            this.status = status;
            return this;
        }

        public SettlementBuilder completed(Integer successful, Integer failed) {
            this.successfulPayouts = successful;
            this.failedPayouts = failed;
            this.status = failed > 0 ? SettlementStatus.PARTIALLY_COMPLETED : SettlementStatus.COMPLETED;
            this.completedAt = LocalDateTime.now();
            return this;
        }

        public Settlement build() {
            Settlement settlement = new Settlement();
            settlement.setId(id);
            settlement.setTotalPayouts(totalPayouts);
            settlement.setTotalAmount(totalAmount);
            settlement.setSuccessfulPayouts(successfulPayouts);
            settlement.setFailedPayouts(failedPayouts);
            settlement.setStatus(status);
            settlement.setCompletedAt(completedAt);
            settlement.setCreatedAt(LocalDateTime.now());
            settlement.setUpdatedAt(LocalDateTime.now());
            return settlement;
        }
    }

    // Factory methods
    public static DriverWalletBuilder wallet() {
        return new DriverWalletBuilder();
    }

    public static PayoutRequestBuilder payout() {
        return new PayoutRequestBuilder();
    }

    public static BankAccountBuilder bankAccount() {
        return new BankAccountBuilder();
    }

    public static EarningsLedgerBuilder ledgerEntry() {
        return new EarningsLedgerBuilder();
    }

    public static SettlementBuilder settlement() {
        return new SettlementBuilder();
    }
}

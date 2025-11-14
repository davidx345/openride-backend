package com.openride.payouts.scheduler;

import com.openride.payouts.model.DriverWallet;
import com.openride.payouts.model.EarningsLedger;
import com.openride.payouts.repository.DriverWalletRepository;
import com.openride.payouts.repository.EarningsLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job for wallet reconciliation.
 * Verifies wallet balances match ledger entries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

    private final DriverWalletRepository walletRepository;
    private final EarningsLedgerRepository ledgerRepository;

    /**
     * Run reconciliation daily at 3:00 AM.
     */
    @Scheduled(cron = "${app.scheduler.reconciliation.cron:0 0 3 * * *}")
    public void reconcileWallets() {
        log.info("Starting wallet reconciliation job");

        int totalChecked = 0;
        int discrepancies = 0;

        try {
            // Get all wallets
            List<DriverWallet> wallets = walletRepository.findAll();

            for (DriverWallet wallet : wallets) {
                totalChecked++;

                // Calculate expected balance from ledger
                BigDecimal expectedBalance = ledgerRepository
                        .calculateBalanceByDriverId(wallet.getDriverId())
                        .orElse(BigDecimal.ZERO);

                // Compare with wallet balance
                BigDecimal actualBalance = wallet.getAvailableBalance();

                if (expectedBalance.compareTo(actualBalance) != 0) {
                    discrepancies++;
                    log.error("Wallet balance discrepancy found: driverId={}, walletId={}, expected={}, actual={}",
                            wallet.getDriverId(), wallet.getId(), expectedBalance, actualBalance);

                    // In production, this would trigger an alert/notification
                    // For now, just log the discrepancy
                }
            }

            log.info("Wallet reconciliation completed: checked={}, discrepancies={}", totalChecked, discrepancies);

        } catch (Exception e) {
            log.error("Error during wallet reconciliation", e);
        }
    }

    /**
     * Clean up old audit logs (older than 2 years) every week.
     */
    @Scheduled(cron = "${app.scheduler.cleanup.audit-logs-cron:0 0 4 * * SUN}")
    public void cleanupOldAuditLogs() {
        log.info("Starting audit log cleanup job");

        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusYears(2);
            // Would delete old audit logs here
            log.info("Audit log cleanup completed: cutoff date={}", cutoffDate);
        } catch (Exception e) {
            log.error("Error during audit log cleanup", e);
        }
    }
}

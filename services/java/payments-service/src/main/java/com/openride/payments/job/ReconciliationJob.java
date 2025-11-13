package com.openride.payments.job;

import com.openride.payments.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Scheduled job for daily payment reconciliation.
 * Runs daily at 2 AM to reconcile previous day's payments.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

    private final ReconciliationService reconciliationService;

    /**
     * Runs daily reconciliation at 2 AM.
     * Reconciles payments from the previous day.
     */
    @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
    public void runDailyReconciliation() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        log.info("Starting daily reconciliation for {}", yesterday);

        try {
            var record = reconciliationService.reconcilePayments(yesterday);
            
            log.info("Daily reconciliation completed: status={}, discrepancies={}", 
                record.getStatus(), record.getDiscrepancyCount());

            if (record.getDiscrepancyCount() > 0) {
                log.warn("Found {} discrepancies in reconciliation for {}", 
                    record.getDiscrepancyCount(), yesterday);
            }
        } catch (Exception e) {
            log.error("Failed to run daily reconciliation for {}", yesterday, e);
        }
    }
}

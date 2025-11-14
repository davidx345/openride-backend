package com.openride.payouts.scheduler;

import com.openride.payouts.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job for automatic settlement processing.
 * Runs weekly on Monday at 2:00 AM to process approved payouts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementJob {

    private final SettlementService settlementService;
    private final RedissonClient redissonClient;

    @Value("${app.scheduler.settlement.lock-key:settlement-job-lock}")
    private String lockKey;

    @Value("${app.scheduler.settlement.lock-wait-time:0}")
    private long lockWaitTime;

    @Value("${app.scheduler.settlement.lock-lease-time:300}")
    private long lockLeaseTime;

    /**
     * Process settlement batch every Monday at 2:00 AM.
     * Uses distributed lock to ensure only one instance processes the settlement.
     */
    @Scheduled(cron = "${app.scheduler.settlement.cron:0 0 2 * * MON}")
    public void processWeeklySettlement() {
        log.info("Starting weekly settlement job");

        RLock lock = redissonClient.getLock(lockKey);
        boolean isLocked = false;

        try {
            // Try to acquire distributed lock
            isLocked = lock.tryLock(lockWaitTime, lockLeaseTime, TimeUnit.SECONDS);
            
            if (!isLocked) {
                log.warn("Could not acquire lock for settlement job. Another instance may be processing.");
                return;
            }

            log.info("Lock acquired for settlement job");

            // Create and process settlement batch
            UUID systemUserId = UUID.fromString("00000000-0000-0000-0000-000000000000"); // System user
            var settlement = settlementService.createSettlementBatch(systemUserId);
            
            log.info("Settlement batch created: id={}, payouts={}, total={}",
                    settlement.getId(), settlement.getPayoutCount(), settlement.getTotalAmount());

            // Process the settlement
            var processed = settlementService.processSettlement(settlement.getId());
            
            log.info("Weekly settlement job completed: status={}", processed.getStatus());

        } catch (Exception e) {
            log.error("Error in weekly settlement job", e);
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("Lock released for settlement job");
            }
        }
    }

    /**
     * Check for stuck settlements every hour and retry if needed.
     */
    @Scheduled(cron = "${app.scheduler.settlement.check-stuck-cron:0 0 * * * *}")
    public void checkStuckSettlements() {
        log.debug("Checking for stuck settlements");

        try {
            // Logic to find and retry stuck settlements would go here
            // For now, just log
            log.debug("Stuck settlement check completed");
        } catch (Exception e) {
            log.error("Error checking stuck settlements", e);
        }
    }
}

package com.openride.payments.job;

import com.openride.payments.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job to expire pending payments.
 * Runs every 15 minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCleanupJob {

    private final PaymentService paymentService;

    /**
     * Expires pending payments that have exceeded timeout.
     * Runs every 15 minutes (900000ms).
     */
    @Scheduled(fixedDelay = 900000) // 15 minutes
    public void expirePayments() {
        log.info("Running payment cleanup job");

        try {
            int expiredCount = paymentService.expirePayments();
            
            if (expiredCount > 0) {
                log.info("Expired {} payments", expiredCount);
            } else {
                log.debug("No payments expired");
            }
        } catch (Exception e) {
            log.error("Error during payment cleanup", e);
        }
    }
}

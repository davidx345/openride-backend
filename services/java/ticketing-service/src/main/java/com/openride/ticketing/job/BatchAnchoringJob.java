package com.openride.ticketing.job;

import com.openride.ticketing.service.MerkleBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job for processing and anchoring Merkle batches to blockchain.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchAnchoringJob {
    
    private final MerkleBatchService merkleBatchService;
    
    /**
     * Process ready batches and anchor to blockchain.
     * Runs hourly by default (configurable via cron expression in application.yml).
     */
    @Scheduled(cron = "${ticketing.batch.anchoring-schedule:0 0 * * * *}")
    public void processAndAnchorBatches() {
        log.info("Starting batch anchoring job");
        
        try {
            merkleBatchService.processReadyBatches();
            
            // Log statistics
            MerkleBatchService.BatchStatistics stats = merkleBatchService.getBatchStatistics();
            log.info("Batch statistics - Pending: {}, Ready: {}, Anchored: {}, Failed: {}",
                    stats.pending(), stats.ready(), stats.anchored(), stats.failed());
            
        } catch (Exception e) {
            log.error("Error in batch anchoring job", e);
        }
        
        log.info("Batch anchoring job completed");
    }
}

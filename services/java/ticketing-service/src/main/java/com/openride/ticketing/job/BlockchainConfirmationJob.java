package com.openride.ticketing.job;

import com.openride.ticketing.blockchain.BlockchainClient;
import com.openride.ticketing.model.BlockchainAnchor;
import com.openride.ticketing.model.BlockchainAnchorStatus;
import com.openride.ticketing.repository.BlockchainAnchorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled job for monitoring blockchain transaction confirmations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlockchainConfirmationJob {
    
    private final BlockchainAnchorRepository anchorRepository;
    private final BlockchainClient blockchainClient;
    
    @Value("${ticketing.blockchain.required-confirmations:12}")
    private Integer requiredConfirmations;
    
    /**
     * Check confirmation status for submitted transactions.
     * Runs every 15 minutes.
     */
    @Scheduled(fixedDelayString = "${ticketing.blockchain.confirmation-check-interval:900000}")
    public void checkConfirmations() {
        log.info("Starting blockchain confirmation check");
        
        try {
            // Get all submitted anchors
            List<BlockchainAnchor> submittedAnchors = anchorRepository
                    .findByStatusOrderByCreatedAtDesc(BlockchainAnchorStatus.SUBMITTED);
            
            log.info("Found {} submitted anchors to check", submittedAnchors.size());
            
            for (BlockchainAnchor anchor : submittedAnchors) {
                checkAnchorConfirmations(anchor);
            }
            
        } catch (Exception e) {
            log.error("Error in blockchain confirmation check job", e);
        }
        
        log.info("Blockchain confirmation check completed");
    }
    
    /**
     * Check confirmations for a single anchor.
     */
    private void checkAnchorConfirmations(BlockchainAnchor anchor) {
        try {
            String txHash = anchor.getTransactionHash();
            
            blockchainClient.getConfirmationCount(txHash)
                    .thenAccept(confirmations -> {
                        log.debug("Transaction {} has {} confirmations", 
                                txHash, confirmations);
                        
                        anchor.setConfirmationCount(confirmations);
                        
                        if (confirmations >= requiredConfirmations) {
                            log.info("Transaction {} confirmed with {} confirmations", 
                                    txHash, confirmations);
                            anchor.markAsConfirmed(confirmations);
                        }
                        
                        anchorRepository.save(anchor);
                    })
                    .exceptionally(e -> {
                        log.error("Error checking confirmations for transaction: {}", 
                                txHash, e);
                        return null;
                    });
            
        } catch (Exception e) {
            log.error("Error checking anchor confirmations: {}", anchor.getId(), e);
        }
    }
}

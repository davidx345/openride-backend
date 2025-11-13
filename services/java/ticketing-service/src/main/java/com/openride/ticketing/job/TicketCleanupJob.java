package com.openride.ticketing.job;

import com.openride.ticketing.model.MerkleBatch;
import com.openride.ticketing.model.MerkleBatchStatus;
import com.openride.ticketing.model.Ticket;
import com.openride.ticketing.model.TicketStatus;
import com.openride.ticketing.repository.MerkleBatchRepository;
import com.openride.ticketing.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job for cleaning up expired tickets and old batches.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketCleanupJob {
    
    private final TicketRepository ticketRepository;
    private final MerkleBatchRepository merkleBatchRepository;
    
    @Value("${ticketing.ticket.retention-days:90}")
    private Integer retentionDays;
    
    /**
     * Expire tickets that have passed their expiry time.
     * Runs every hour.
     */
    @Scheduled(cron = "${ticketing.cleanup.expire-schedule:0 0 * * * *}")
    @Transactional
    public void expireTickets() {
        log.info("Starting ticket expiration job");
        
        try {
            // Find all VALID tickets that have expired
            List<Ticket> expiredTickets = ticketRepository
                    .findExpiredTicketsByStatus(TicketStatus.VALID, LocalDateTime.now());
            
            log.info("Found {} expired tickets", expiredTickets.size());
            
            for (Ticket ticket : expiredTickets) {
                ticket.setStatus(TicketStatus.EXPIRED);
                ticketRepository.save(ticket);
                log.debug("Expired ticket: {}", ticket.getId());
            }
            
        } catch (Exception e) {
            log.error("Error in ticket expiration job", e);
        }
        
        log.info("Ticket expiration job completed");
    }
    
    /**
     * Clean up old tickets and batches beyond retention period.
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "${ticketing.cleanup.deletion-schedule:0 0 2 * * *}")
    @Transactional
    public void cleanupOldData() {
        log.info("Starting data cleanup job");
        
        try {
            LocalDateTime retentionCutoff = LocalDateTime.now().minusDays(retentionDays);
            
            // Count tickets to delete
            long ticketCount = ticketRepository.countByCreatedAtBefore(retentionCutoff);
            log.info("Found {} old tickets to delete (created before {})", 
                    ticketCount, retentionCutoff);
            
            // Delete old tickets (cascades to verification logs)
            if (ticketCount > 0) {
                ticketRepository.deleteByCreatedAtBefore(retentionCutoff);
                log.info("Deleted {} old tickets", ticketCount);
            }
            
            // Clean up old failed batches
            List<MerkleBatch> failedBatches = merkleBatchRepository
                    .findByStatusOrderByCreatedAtDesc(MerkleBatchStatus.FAILED);
            
            long deletedBatches = 0;
            for (MerkleBatch batch : failedBatches) {
                if (batch.getCreatedAt().isBefore(retentionCutoff)) {
                    merkleBatchRepository.delete(batch);
                    deletedBatches++;
                }
            }
            
            if (deletedBatches > 0) {
                log.info("Deleted {} old failed batches", deletedBatches);
            }
            
        } catch (Exception e) {
            log.error("Error in data cleanup job", e);
        }
        
        log.info("Data cleanup job completed");
    }
}

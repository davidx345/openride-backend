package com.openride.ticketing.service;

import com.openride.ticketing.blockchain.BlockchainClient;
import com.openride.ticketing.crypto.MerkleTree;
import com.openride.ticketing.model.*;
import com.openride.ticketing.repository.MerkleBatchRepository;
import com.openride.ticketing.repository.MerkleProofRepository;
import com.openride.ticketing.repository.TicketRepository;
import com.openride.ticketing.repository.BlockchainAnchorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing Merkle batches and blockchain anchoring.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerkleBatchService {
    
    private final MerkleBatchRepository merkleBatchRepository;
    private final MerkleProofRepository merkleProofRepository;
    private final TicketRepository ticketRepository;
    private final BlockchainAnchorRepository blockchainAnchorRepository;
    private final BlockchainClient blockchainClient;
    
    @Value("${ticketing.batch.max-size:100}")
    private Integer maxBatchSize;
    
    /**
     * Add a ticket to the current pending batch.
     * Creates a new batch if none exists or current batch is full.
     *
     * @param ticket the ticket to add
     */
    @Transactional
    public void addTicketToBatch(Ticket ticket) {
        log.debug("Adding ticket to batch: {}", ticket.getId());
        
        // Find or create pending batch
        MerkleBatch batch = findOrCreatePendingBatch();
        
        // Associate ticket with batch
        ticket.setMerkleBatch(batch);
        ticketRepository.save(ticket);
        
        // Increment ticket count
        batch.addTicket();
        merkleBatchRepository.save(batch);
        
        log.debug("Ticket added to batch: {} (count: {})", batch.getId(), batch.getTicketCount());
        
        // Check if batch is ready for anchoring
        if (batch.getTicketCount() >= maxBatchSize) {
            log.info("Batch reached max size, marking as ready: {}", batch.getId());
            batch.setStatus(MerkleBatchStatus.READY);
            merkleBatchRepository.save(batch);
        }
    }
    
    /**
     * Build Merkle tree for a batch and generate proofs.
     *
     * @param batchId the batch ID
     */
    @Transactional
    public void buildMerkleTree(UUID batchId) {
        log.info("Building Merkle tree for batch: {}", batchId);
        
        MerkleBatch batch = merkleBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        
        if (batch.getStatus() != MerkleBatchStatus.READY) {
            log.warn("Batch is not ready for Merkle tree building: {}", batchId);
            return;
        }
        
        // Get all tickets in batch
        List<Ticket> tickets = ticketRepository.findByMerkleBatchId(batchId);
        
        if (tickets.isEmpty()) {
            log.warn("No tickets found in batch: {}", batchId);
            return;
        }
        
        // Extract ticket hashes
        List<String> ticketHashes = tickets.stream()
                .map(Ticket::getHash)
                .collect(Collectors.toList());
        
        // Build Merkle tree
        MerkleTree merkleTree = new MerkleTree(ticketHashes);
        String merkleRoot = merkleTree.getRoot();
        
        batch.setMerkleRoot(merkleRoot);
        batch.setStatus(MerkleBatchStatus.BUILDING);
        merkleBatchRepository.save(batch);
        
        log.info("Merkle tree built for batch: {} - Root: {}", batchId, merkleRoot);
        
        // Generate and store proofs for each ticket
        for (int i = 0; i < tickets.size(); i++) {
            Ticket ticket = tickets.get(i);
            List<String> proof = merkleTree.generateProof(i);
            
            MerkleProof merkleProof = new MerkleProof();
            merkleProof.setTicket(ticket);
            merkleProof.setMerkleBatch(batch);
            merkleProof.setLeafIndex(i);
            merkleProof.setProofPath(proof);
            
            merkleProofRepository.save(merkleProof);
            
            log.debug("Generated Merkle proof for ticket: {} (index: {})", ticket.getId(), i);
        }
        
        // Mark batch as ready for anchoring
        batch.setStatus(MerkleBatchStatus.READY);
        merkleBatchRepository.save(batch);
        
        log.info("Merkle tree and proofs generated for batch: {}", batchId);
    }
    
    /**
     * Anchor Merkle root to blockchain.
     *
     * @param batchId the batch ID
     */
    @Transactional
    public void anchorToBlockchain(UUID batchId) {
        log.info("Anchoring batch to blockchain: {}", batchId);
        
        MerkleBatch batch = merkleBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));
        
        if (batch.getMerkleRoot() == null) {
            log.error("Cannot anchor batch without Merkle root: {}", batchId);
            throw new IllegalStateException("Batch must have Merkle root before anchoring");
        }
        
        if (batch.getBlockchainAnchor() != null) {
            log.warn("Batch already anchored: {}", batchId);
            return;
        }
        
        try {
            // Submit to blockchain
            blockchainClient.anchorMerkleRoot(batch)
                    .thenAccept(anchor -> {
                        // Save blockchain anchor
                        anchor.setMerkleBatch(batch);
                        blockchainAnchorRepository.save(anchor);
                        
                        // Update batch status
                        if (anchor.getStatus() == BlockchainAnchorStatus.SUBMITTED) {
                            batch.setStatus(MerkleBatchStatus.ANCHORED);
                        } else {
                            batch.setStatus(MerkleBatchStatus.FAILED);
                        }
                        merkleBatchRepository.save(batch);
                        
                        log.info("Batch anchored to blockchain: {} - TX: {}", 
                                batchId, anchor.getTransactionHash());
                    })
                    .exceptionally(e -> {
                        log.error("Failed to anchor batch to blockchain: {}", batchId, e);
                        batch.setStatus(MerkleBatchStatus.FAILED);
                        merkleBatchRepository.save(batch);
                        return null;
                    });
            
        } catch (Exception e) {
            log.error("Error anchoring batch to blockchain: {}", batchId, e);
            batch.setStatus(MerkleBatchStatus.FAILED);
            merkleBatchRepository.save(batch);
        }
    }
    
    /**
     * Process all ready batches (build Merkle trees and anchor).
     */
    @Transactional
    public void processReadyBatches() {
        log.info("Processing ready batches");
        
        List<MerkleBatch> readyBatches = merkleBatchRepository
                .findByStatusOrderByCreatedAtDesc(MerkleBatchStatus.READY);
        
        log.info("Found {} ready batches", readyBatches.size());
        
        for (MerkleBatch batch : readyBatches) {
            try {
                // Build Merkle tree if not already built
                if (batch.getMerkleRoot() == null) {
                    buildMerkleTree(batch.getId());
                }
                
                // Anchor to blockchain
                anchorToBlockchain(batch.getId());
                
            } catch (Exception e) {
                log.error("Error processing batch: {}", batch.getId(), e);
            }
        }
    }
    
    /**
     * Find or create a pending batch.
     */
    private MerkleBatch findOrCreatePendingBatch() {
        List<MerkleBatch> pendingBatches = merkleBatchRepository
                .findByStatusOrderByCreatedAtDesc(MerkleBatchStatus.PENDING);
        
        // Find a pending batch that's not full
        for (MerkleBatch batch : pendingBatches) {
            if (batch.getTicketCount() < maxBatchSize) {
                return batch;
            }
        }
        
        // Create new batch
        MerkleBatch newBatch = new MerkleBatch();
        newBatch.setStatus(MerkleBatchStatus.PENDING);
        newBatch.setTicketCount(0);
        
        return merkleBatchRepository.save(newBatch);
    }
    
    /**
     * Get batch statistics.
     */
    public BatchStatistics getBatchStatistics() {
        long pending = merkleBatchRepository.countByStatus(MerkleBatchStatus.PENDING);
        long ready = merkleBatchRepository.countByStatus(MerkleBatchStatus.READY);
        long anchored = merkleBatchRepository.countByStatus(MerkleBatchStatus.ANCHORED);
        long failed = merkleBatchRepository.countByStatus(MerkleBatchStatus.FAILED);
        
        return new BatchStatistics(pending, ready, anchored, failed);
    }
    
    /**
     * Batch statistics record.
     */
    public record BatchStatistics(
            long pending,
            long ready,
            long anchored,
            long failed
    ) {}
}

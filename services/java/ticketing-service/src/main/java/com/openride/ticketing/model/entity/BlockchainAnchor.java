package com.openride.ticketing.model.entity;

import com.openride.ticketing.model.enums.BlockchainAnchorStatus;
import com.openride.ticketing.model.enums.BlockchainType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Blockchain anchor entity representing an on-chain transaction
 * that anchors a Merkle batch to the blockchain.
 * 
 * This provides:
 * - Immutable record of batch anchoring
 * - Transaction tracking
 * - Gas cost monitoring
 * - Confirmation tracking
 * - Retry logic support
 */
@Entity
@Table(name = "blockchain_anchors", indexes = {
    @Index(name = "idx_blockchain_anchors_batch_id", columnList = "merkle_batch_id"),
    @Index(name = "idx_blockchain_anchors_status", columnList = "status"),
    @Index(name = "idx_blockchain_anchors_tx_hash", columnList = "transaction_hash"),
    @Index(name = "idx_blockchain_anchors_block_number", columnList = "block_number"),
    @Index(name = "idx_blockchain_anchors_submitted_at", columnList = "submitted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockchainAnchor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merkle_batch_id", nullable = false, 
                foreignKey = @ForeignKey(name = "fk_anchor_batch"))
    private MerkleBatch merkleBatch;

    @Enumerated(EnumType.STRING)
    @Column(name = "blockchain_type", nullable = false, length = 50)
    private BlockchainType blockchainType;

    // Transaction details
    
    @Column(name = "transaction_hash", length = 66)
    private String transactionHash;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "confirmation_count", nullable = false)
    @Builder.Default
    private Integer confirmationCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BlockchainAnchorStatus status = BlockchainAnchorStatus.PENDING;

    // Gas and cost tracking
    
    @Column(name = "gas_price")
    private Long gasPrice;

    @Column(name = "gas_limit")
    private Long gasLimit;

    @Column(name = "gas_used")
    private Long gasUsed;

    // Error handling
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    // Timestamps
    
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Check if anchor is pending submission.
     * 
     * @return true if status is PENDING
     */
    public boolean isPending() {
        return status == BlockchainAnchorStatus.PENDING;
    }

    /**
     * Check if anchor has been submitted.
     * 
     * @return true if status is SUBMITTED
     */
    public boolean isSubmitted() {
        return status == BlockchainAnchorStatus.SUBMITTED;
    }

    /**
     * Check if anchor has been confirmed.
     * 
     * @return true if status is CONFIRMED
     */
    public boolean isConfirmed() {
        return status == BlockchainAnchorStatus.CONFIRMED;
    }

    /**
     * Check if anchor has failed.
     * 
     * @return true if status is FAILED
     */
    public boolean isFailed() {
        return status == BlockchainAnchorStatus.FAILED;
    }

    /**
     * Mark anchor as submitted.
     * 
     * @param txHash the blockchain transaction hash
     */
    public void markAsSubmitted(String txHash) {
        this.status = BlockchainAnchorStatus.SUBMITTED;
        this.transactionHash = txHash;
        this.submittedAt = LocalDateTime.now();
    }

    /**
     * Mark anchor as confirmed.
     * 
     * @param blockNum the block number
     * @param confirmations the number of confirmations
     */
    public void markAsConfirmed(Long blockNum, Integer confirmations) {
        this.status = BlockchainAnchorStatus.CONFIRMED;
        this.blockNumber = blockNum;
        this.confirmationCount = confirmations;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * Mark anchor as failed.
     * 
     * @param error the error message
     */
    public void markAsFailed(String error) {
        this.status = BlockchainAnchorStatus.FAILED;
        this.errorMessage = error;
        this.retryCount++;
    }

    /**
     * Increment confirmation count.
     */
    public void incrementConfirmations() {
        this.confirmationCount++;
    }

    /**
     * Calculate gas cost in Wei (for Ethereum/Polygon).
     * 
     * @return gas cost or null if not available
     */
    public Long calculateGasCost() {
        if (gasUsed != null && gasPrice != null) {
            return gasUsed * gasPrice;
        }
        return null;
    }
}

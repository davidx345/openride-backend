package com.openride.ticketing.model.enums;

/**
 * Status of blockchain anchor transaction.
 */
public enum BlockchainAnchorStatus {
    /**
     * Anchor waiting to be submitted.
     */
    PENDING,
    
    /**
     * Transaction submitted to blockchain network.
     */
    SUBMITTED,
    
    /**
     * Transaction confirmed on blockchain.
     */
    CONFIRMED,
    
    /**
     * Transaction failed (will be retried).
     */
    FAILED,
    
    /**
     * Transaction expired (stuck in mempool too long).
     */
    EXPIRED
}

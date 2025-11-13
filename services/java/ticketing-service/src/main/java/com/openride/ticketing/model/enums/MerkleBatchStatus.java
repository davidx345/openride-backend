package com.openride.ticketing.model.enums;

/**
 * Status of a Merkle batch in its lifecycle.
 */
public enum MerkleBatchStatus {
    /**
     * Batch is being accumulated (collecting tickets).
     */
    PENDING,
    
    /**
     * Merkle tree is being constructed.
     */
    BUILDING,
    
    /**
     * Merkle tree ready for blockchain anchoring.
     */
    READY,
    
    /**
     * Successfully anchored to blockchain.
     */
    ANCHORED,
    
    /**
     * Failed to anchor (will be retried).
     */
    FAILED
}

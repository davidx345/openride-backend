package com.openride.payouts.model.enums;

/**
 * Status of a settlement batch.
 */
public enum SettlementStatus {
    /**
     * Settlement batch created, ready for processing
     */
    PENDING,
    
    /**
     * Currently processing payouts in this batch
     */
    PROCESSING,
    
    /**
     * All payouts in batch successfully processed
     */
    COMPLETED,
    
    /**
     * One or more payouts failed, batch marked as failed
     */
    FAILED
}

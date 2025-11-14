package com.openride.payouts.model.enums;

/**
 * Status of a payout request throughout its lifecycle.
 * 
 * Workflow:
 * PENDING -> APPROVED/REJECTED -> PROCESSING -> COMPLETED/FAILED
 */
public enum PayoutStatus {
    /**
     * Payout request created by driver, awaiting admin review
     */
    PENDING,
    
    /**
     * Approved by admin, ready for settlement processing
     */
    APPROVED,
    
    /**
     * Rejected by admin with notes
     */
    REJECTED,
    
    /**
     * Currently being processed by settlement batch
     */
    PROCESSING,
    
    /**
     * Successfully transferred to driver's bank account
     */
    COMPLETED,
    
    /**
     * Transfer failed, amount returned to wallet
     */
    FAILED
}

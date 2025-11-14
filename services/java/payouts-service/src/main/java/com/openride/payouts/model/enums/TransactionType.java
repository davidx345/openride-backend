package com.openride.payouts.model.enums;

/**
 * Type of transaction in the ledger.
 */
public enum TransactionType {
    /**
     * Driver earnings from completed trip
     */
    EARNING,
    
    /**
     * Payout to driver's bank account
     */
    PAYOUT,
    
    /**
     * Refund to driver's wallet
     */
    REFUND,
    
    /**
     * Manual adjustment by admin
     */
    ADJUSTMENT
}

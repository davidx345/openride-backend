package com.openride.payments.model;

/**
 * Enum representing the lifecycle status of a payment transaction.
 * 
 * State transition flow:
 * INITIATED → PENDING → SUCCESS → COMPLETED
 *                   ↓
 *                 FAILED
 *                   ↓
 *              REFUNDED (from SUCCESS)
 */
public enum PaymentStatus {
    /**
     * Payment request created, waiting for Korapay initialization.
     */
    INITIATED,
    
    /**
     * Payment sent to Korapay, waiting for customer to complete payment.
     * Typically expires after 15 minutes.
     */
    PENDING,
    
    /**
     * Payment successfully completed and verified by Korapay.
     * Booking should be confirmed at this point.
     */
    SUCCESS,
    
    /**
     * Payment failed due to various reasons:
     * - Insufficient funds
     * - Customer cancelled
     * - Payment expired
     * - Network error
     */
    FAILED,
    
    /**
     * Payment was successful but later refunded to customer.
     */
    REFUNDED,
    
    /**
     * Final state after successful payment and booking confirmation.
     */
    COMPLETED;
    
    /**
     * Checks if the status represents a terminal state (no further transitions allowed).
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == REFUNDED;
    }
    
    /**
     * Checks if a payment in this status can be refunded.
     */
    public boolean isRefundable() {
        return this == SUCCESS || this == COMPLETED;
    }
}

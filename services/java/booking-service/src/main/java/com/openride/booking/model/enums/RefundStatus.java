package com.openride.booking.model.enums;

/**
 * Refund status enum
 */
public enum RefundStatus {
    /** No refund applicable */
    NONE,
    
    /** Refund pending processing */
    PENDING,
    
    /** Refund processed successfully */
    PROCESSED,
    
    /** Refund failed */
    FAILED
}

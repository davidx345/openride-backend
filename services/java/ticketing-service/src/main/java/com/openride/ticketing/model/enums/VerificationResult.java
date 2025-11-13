package com.openride.ticketing.model.enums;

/**
 * Result of ticket verification attempt.
 */
public enum VerificationResult {
    /**
     * Ticket is valid and can be used.
     */
    VALID,
    
    /**
     * Ticket signature or hash is invalid (tampered).
     */
    INVALID,
    
    /**
     * Ticket has expired.
     */
    EXPIRED,
    
    /**
     * Ticket has been revoked.
     */
    REVOKED,
    
    /**
     * Ticket not found in system.
     */
    NOT_FOUND
}

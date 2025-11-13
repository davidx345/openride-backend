package com.openride.ticketing.model.enums;

/**
 * Status of a ticket in its lifecycle.
 */
public enum TicketStatus {
    /**
     * Ticket has been generated but not yet validated or used.
     */
    PENDING,
    
    /**
     * Ticket is valid and can be used for travel.
     */
    VALID,
    
    /**
     * Ticket has been used/scanned for boarding.
     */
    USED,
    
    /**
     * Ticket has expired (past trip date + validity period).
     */
    EXPIRED,
    
    /**
     * Ticket has been revoked (due to booking cancellation or refund).
     */
    REVOKED
}

package com.openride.booking.model.enums;

import java.util.Map;
import java.util.Set;

/**
 * Booking status enum with state machine transitions
 * 
 * State Flow:
 * 1. PENDING → HELD → PAYMENT_INITIATED → PAID → CONFIRMED
 * 2. CONFIRMED → CHECKED_IN → COMPLETED
 * 3. Any non-terminal → CANCELLED → REFUNDED
 * 4. PENDING/HELD → EXPIRED
 */
public enum BookingStatus {
    /** Initial state when booking created */
    PENDING,
    
    /** Seats held in Redis with TTL */
    HELD,
    
    /** Payment initiated with provider */
    PAYMENT_INITIATED,
    
    /** Payment confirmed by provider */
    PAID,
    
    /** Booking confirmed, ticket generated */
    CONFIRMED,
    
    /** Rider checked in at pickup point */
    CHECKED_IN,
    
    /** Trip completed successfully */
    COMPLETED,
    
    /** Booking cancelled by rider or system */
    CANCELLED,
    
    /** Refund processed */
    REFUNDED,
    
    /** Booking expired without payment */
    EXPIRED;

    /**
     * Valid state transitions map
     * Each status can only transition to specific next statuses
     */
    private static final Map<BookingStatus, Set<BookingStatus>> ALLOWED_TRANSITIONS = Map.ofEntries(
        Map.entry(PENDING, Set.of(HELD, CANCELLED, EXPIRED)),
        Map.entry(HELD, Set.of(PAYMENT_INITIATED, CANCELLED, EXPIRED)),
        Map.entry(PAYMENT_INITIATED, Set.of(PAID, CANCELLED, EXPIRED)),
        Map.entry(PAID, Set.of(CONFIRMED, REFUNDED)),
        Map.entry(CONFIRMED, Set.of(CHECKED_IN, CANCELLED, REFUNDED)),
        Map.entry(CHECKED_IN, Set.of(COMPLETED, CANCELLED)),
        Map.entry(COMPLETED, Set.of()),  // Terminal state
        Map.entry(CANCELLED, Set.of(REFUNDED)),
        Map.entry(REFUNDED, Set.of()),   // Terminal state
        Map.entry(EXPIRED, Set.of())     // Terminal state
    );

    /**
     * Check if transition to new status is allowed
     * 
     * @param newStatus Target status
     * @return true if transition is valid
     */
    public boolean canTransitionTo(BookingStatus newStatus) {
        Set<BookingStatus> allowedNext = ALLOWED_TRANSITIONS.get(this);
        return allowedNext != null && allowedNext.contains(newStatus);
    }

    /**
     * Get allowed next statuses
     * 
     * @return Set of statuses this status can transition to
     */
    public Set<BookingStatus> getAllowedTransitions() {
        return ALLOWED_TRANSITIONS.getOrDefault(this, Set.of());
    }

    /**
     * Check if this is a terminal status
     * 
     * @return true if no further transitions allowed
     */
    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }

    /**
     * Check if this status represents an active booking
     * 
     * @return true if booking is active
     */
    public boolean isActive() {
        return this == CONFIRMED || this == CHECKED_IN;
    }

    /**
     * Check if booking can be cancelled
     * 
     * @return true if cancellation is allowed from this status
     */
    public boolean isCancellable() {
        return this == PENDING || this == HELD || this == PAYMENT_INITIATED 
            || this == CONFIRMED || this == CHECKED_IN;
    }

    /**
     * Check if seats are currently held
     * 
     * @return true if seats are in held state
     */
    public boolean hasSeatsHeld() {
        return this == HELD || this == PAYMENT_INITIATED;
    }
}

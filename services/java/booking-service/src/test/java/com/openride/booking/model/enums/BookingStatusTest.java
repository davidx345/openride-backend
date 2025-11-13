package com.openride.booking.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BookingStatus state machine
 */
class BookingStatusTest {

    @Test
    void testValidTransitions() {
        // PENDING → HELD
        assertTrue(BookingStatus.PENDING.canTransitionTo(BookingStatus.HELD));
        
        // HELD → PAYMENT_INITIATED
        assertTrue(BookingStatus.HELD.canTransitionTo(BookingStatus.PAYMENT_INITIATED));
        
        // PAYMENT_INITIATED → PAID
        assertTrue(BookingStatus.PAYMENT_INITIATED.canTransitionTo(BookingStatus.PAID));
        
        // PAID → CONFIRMED
        assertTrue(BookingStatus.PAID.canTransitionTo(BookingStatus.CONFIRMED));
        
        // CONFIRMED → CHECKED_IN
        assertTrue(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CHECKED_IN));
        
        // CHECKED_IN → COMPLETED
        assertTrue(BookingStatus.CHECKED_IN.canTransitionTo(BookingStatus.COMPLETED));
    }

    @Test
    void testCancellationTransitions() {
        // HELD → CANCELLED
        assertTrue(BookingStatus.HELD.canTransitionTo(BookingStatus.CANCELLED));
        
        // PAYMENT_INITIATED → CANCELLED
        assertTrue(BookingStatus.PAYMENT_INITIATED.canTransitionTo(BookingStatus.CANCELLED));
        
        // CONFIRMED → CANCELLED
        assertTrue(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CANCELLED));
        
        // CHECKED_IN → CANCELLED
        assertTrue(BookingStatus.CHECKED_IN.canTransitionTo(BookingStatus.CANCELLED));
    }

    @Test
    void testExpirationTransitions() {
        // PENDING → EXPIRED
        assertTrue(BookingStatus.PENDING.canTransitionTo(BookingStatus.EXPIRED));
        
        // HELD → EXPIRED
        assertTrue(BookingStatus.HELD.canTransitionTo(BookingStatus.EXPIRED));
    }

    @Test
    void testInvalidTransitions() {
        // Cannot go backwards
        assertFalse(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.PENDING));
        assertFalse(BookingStatus.PAID.canTransitionTo(BookingStatus.HELD));
        
        // Cannot transition from terminal states
        assertFalse(BookingStatus.COMPLETED.canTransitionTo(BookingStatus.CONFIRMED));
        assertFalse(BookingStatus.CANCELLED.canTransitionTo(BookingStatus.HELD));
        assertFalse(BookingStatus.EXPIRED.canTransitionTo(BookingStatus.PENDING));
    }

    @Test
    void testTerminalStates() {
        assertTrue(BookingStatus.COMPLETED.isTerminal());
        assertTrue(BookingStatus.CANCELLED.isTerminal());
        assertTrue(BookingStatus.EXPIRED.isTerminal());
        assertTrue(BookingStatus.FAILED.isTerminal());
        
        assertFalse(BookingStatus.PENDING.isTerminal());
        assertFalse(BookingStatus.HELD.isTerminal());
        assertFalse(BookingStatus.CONFIRMED.isTerminal());
    }

    @Test
    void testCancellableStates() {
        assertTrue(BookingStatus.HELD.isCancellable());
        assertTrue(BookingStatus.PAYMENT_INITIATED.isCancellable());
        assertTrue(BookingStatus.CONFIRMED.isCancellable());
        assertTrue(BookingStatus.CHECKED_IN.isCancellable());
        
        assertFalse(BookingStatus.PENDING.isCancellable());
        assertFalse(BookingStatus.COMPLETED.isCancellable());
        assertFalse(BookingStatus.CANCELLED.isCancellable());
    }
}

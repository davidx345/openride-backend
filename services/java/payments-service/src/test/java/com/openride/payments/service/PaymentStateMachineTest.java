package com.openride.payments.service;

import com.openride.payments.exception.InvalidStateTransitionException;
import com.openride.payments.model.Payment;
import com.openride.payments.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentStateMachine.
 */
@DisplayName("PaymentStateMachine Tests")
class PaymentStateMachineTest {

    private PaymentStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new PaymentStateMachine();
    }

    @Test
    @DisplayName("Should transition from INITIATED to PENDING")
    void shouldTransitionFromInitiatedToPending() {
        Payment payment = createPayment(PaymentStatus.INITIATED);

        stateMachine.transition(payment, PaymentStatus.PENDING);

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    @DisplayName("Should transition from INITIATED to FAILED")
    void shouldTransitionFromInitiatedToFailed() {
        Payment payment = createPayment(PaymentStatus.INITIATED);

        stateMachine.transition(payment, PaymentStatus.FAILED);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    @DisplayName("Should transition from PENDING to SUCCESS")
    void shouldTransitionFromPendingToSuccess() {
        Payment payment = createPayment(PaymentStatus.PENDING);

        stateMachine.transition(payment, PaymentStatus.SUCCESS);

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    }

    @Test
    @DisplayName("Should transition from PENDING to FAILED")
    void shouldTransitionFromPendingToFailed() {
        Payment payment = createPayment(PaymentStatus.PENDING);

        stateMachine.transition(payment, PaymentStatus.FAILED);

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    @DisplayName("Should transition from SUCCESS to REFUNDED")
    void shouldTransitionFromSuccessToRefunded() {
        Payment payment = createPayment(PaymentStatus.SUCCESS);

        stateMachine.transition(payment, PaymentStatus.REFUNDED);

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
    }

    @Test
    @DisplayName("Should transition from SUCCESS to COMPLETED")
    void shouldTransitionFromSuccessToCompleted() {
        Payment payment = createPayment(PaymentStatus.SUCCESS);

        stateMachine.transition(payment, PaymentStatus.COMPLETED);

        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
    }

    @Test
    @DisplayName("Should reject transition from FAILED to SUCCESS")
    void shouldRejectTransitionFromFailedToSuccess() {
        Payment payment = createPayment(PaymentStatus.FAILED);

        assertThrows(InvalidStateTransitionException.class, () -> {
            stateMachine.transition(payment, PaymentStatus.SUCCESS);
        });
    }

    @Test
    @DisplayName("Should reject transition from REFUNDED to SUCCESS")
    void shouldRejectTransitionFromRefundedToSuccess() {
        Payment payment = createPayment(PaymentStatus.REFUNDED);

        assertThrows(InvalidStateTransitionException.class, () -> {
            stateMachine.transition(payment, PaymentStatus.SUCCESS);
        });
    }

    @Test
    @DisplayName("Should reject transition from PENDING to REFUNDED")
    void shouldRejectTransitionFromPendingToRefunded() {
        Payment payment = createPayment(PaymentStatus.PENDING);

        assertThrows(InvalidStateTransitionException.class, () -> {
            stateMachine.transition(payment, PaymentStatus.REFUNDED);
        });
    }

    @Test
    @DisplayName("Should reject transition from INITIATED to SUCCESS")
    void shouldRejectTransitionFromInitiatedToSuccess() {
        Payment payment = createPayment(PaymentStatus.INITIATED);

        assertThrows(InvalidStateTransitionException.class, () -> {
            stateMachine.transition(payment, PaymentStatus.SUCCESS);
        });
    }

    @Test
    @DisplayName("Should accept same state transition")
    void shouldAcceptSameStateTransition() {
        Payment payment = createPayment(PaymentStatus.PENDING);

        assertDoesNotThrow(() -> {
            stateMachine.transition(payment, PaymentStatus.PENDING);
        });

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    @Test
    @DisplayName("Should validate all valid transitions")
    void shouldValidateAllValidTransitions() {
        // INITIATED -> PENDING
        assertTrue(stateMachine.canTransition(PaymentStatus.INITIATED, PaymentStatus.PENDING));
        
        // INITIATED -> FAILED
        assertTrue(stateMachine.canTransition(PaymentStatus.INITIATED, PaymentStatus.FAILED));
        
        // PENDING -> SUCCESS
        assertTrue(stateMachine.canTransition(PaymentStatus.PENDING, PaymentStatus.SUCCESS));
        
        // PENDING -> FAILED
        assertTrue(stateMachine.canTransition(PaymentStatus.PENDING, PaymentStatus.FAILED));
        
        // SUCCESS -> REFUNDED
        assertTrue(stateMachine.canTransition(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED));
        
        // SUCCESS -> COMPLETED
        assertTrue(stateMachine.canTransition(PaymentStatus.SUCCESS, PaymentStatus.COMPLETED));
    }

    @Test
    @DisplayName("Should reject all invalid transitions")
    void shouldRejectAllInvalidTransitions() {
        // FAILED -> SUCCESS
        assertFalse(stateMachine.canTransition(PaymentStatus.FAILED, PaymentStatus.SUCCESS));
        
        // REFUNDED -> SUCCESS
        assertFalse(stateMachine.canTransition(PaymentStatus.REFUNDED, PaymentStatus.SUCCESS));
        
        // PENDING -> REFUNDED
        assertFalse(stateMachine.canTransition(PaymentStatus.PENDING, PaymentStatus.REFUNDED));
        
        // INITIATED -> SUCCESS
        assertFalse(stateMachine.canTransition(PaymentStatus.INITIATED, PaymentStatus.SUCCESS));
        
        // COMPLETED -> FAILED
        assertFalse(stateMachine.canTransition(PaymentStatus.COMPLETED, PaymentStatus.FAILED));
    }

    private Payment createPayment(PaymentStatus status) {
        return Payment.builder()
            .id(UUID.randomUUID())
            .bookingId(UUID.randomUUID())
            .riderId(UUID.randomUUID())
            .amount(new BigDecimal("5000.00"))
            .currency("NGN")
            .status(status)
            .korapayReference("TEST_REF_" + System.currentTimeMillis())
            .idempotencyKey("test-key-" + UUID.randomUUID())
            .initiatedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(15))
            .build();
    }
}

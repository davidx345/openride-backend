package com.openride.payments.service;

import com.openride.payments.exception.InvalidStateTransitionException;
import com.openride.payments.model.Payment;
import com.openride.payments.model.PaymentStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * State machine for managing payment status transitions.
 * Ensures only valid state transitions are allowed.
 *
 * Valid transitions:
 * INITIATED → PENDING
 * PENDING → SUCCESS
 * PENDING → FAILED
 * SUCCESS → REFUNDED
 * SUCCESS → COMPLETED
 */
@Slf4j
@Component
public class PaymentStateMachine {

    private final Map<PaymentStatus, Set<PaymentStatus>> allowedTransitions;

    public PaymentStateMachine() {
        allowedTransitions = new EnumMap<>(PaymentStatus.class);
        
        // Define allowed transitions
        allowedTransitions.put(PaymentStatus.INITIATED, EnumSet.of(PaymentStatus.PENDING, PaymentStatus.FAILED));
        allowedTransitions.put(PaymentStatus.PENDING, EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED));
        allowedTransitions.put(PaymentStatus.SUCCESS, EnumSet.of(PaymentStatus.REFUNDED, PaymentStatus.COMPLETED));
        allowedTransitions.put(PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class));
        allowedTransitions.put(PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));
        allowedTransitions.put(PaymentStatus.COMPLETED, EnumSet.noneOf(PaymentStatus.class));
    }

    /**
     * Validates if a state transition is allowed.
     *
     * @param current current payment status
     * @param next desired payment status
     * @throws InvalidStateTransitionException if transition is not allowed
     */
    public void validateTransition(PaymentStatus current, PaymentStatus next) {
        Set<PaymentStatus> allowed = allowedTransitions.get(current);
        
        if (allowed == null || !allowed.contains(next)) {
            String message = String.format(
                "Invalid state transition: %s → %s. Allowed transitions from %s: %s",
                current, next, current, allowed
            );
            log.error(message);
            throw new InvalidStateTransitionException(message);
        }
        
        log.debug("Valid state transition: {} → {}", current, next);
    }

    /**
     * Transitions payment to new status if allowed.
     *
     * @param payment payment to transition
     * @param newStatus new status
     * @throws InvalidStateTransitionException if transition is not allowed
     */
    public void transition(Payment payment, PaymentStatus newStatus) {
        PaymentStatus currentStatus = payment.getStatus();
        validateTransition(currentStatus, newStatus);
        payment.setStatus(newStatus);
        log.info("Payment {} transitioned: {} → {}", payment.getId(), currentStatus, newStatus);
    }

    /**
     * Checks if transition from current to next status is allowed.
     *
     * @param current current status
     * @param next desired status
     * @return true if transition is allowed
     */
    public boolean canTransition(PaymentStatus current, PaymentStatus next) {
        Set<PaymentStatus> allowed = allowedTransitions.get(current);
        return allowed != null && allowed.contains(next);
    }

    /**
     * Gets all allowed next states for a given status.
     *
     * @param current current status
     * @return set of allowed next states
     */
    public Set<PaymentStatus> getAllowedNextStates(PaymentStatus current) {
        return allowedTransitions.getOrDefault(current, EnumSet.noneOf(PaymentStatus.class));
    }
}

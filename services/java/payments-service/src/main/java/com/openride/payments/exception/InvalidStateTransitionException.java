package com.openride.payments.exception;

/**
 * Exception thrown when an invalid payment state transition is attempted.
 * For example, trying to transition from FAILED to SUCCESS.
 */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }

    public InvalidStateTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}

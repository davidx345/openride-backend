package com.openride.payouts.exception;

/**
 * Base exception for all payouts service exceptions.
 */
public class PayoutsException extends RuntimeException {

    public PayoutsException(String message) {
        super(message);
    }

    public PayoutsException(String message, Throwable cause) {
        super(message, cause);
    }
}

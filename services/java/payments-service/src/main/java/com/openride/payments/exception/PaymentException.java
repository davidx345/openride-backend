package com.openride.payments.exception;

/**
 * Exception thrown when a payment operation fails.
 * This could be due to Korapay API errors, network issues, or business rule violations.
 */
public class PaymentException extends RuntimeException {

    public PaymentException(String message) {
        super(message);
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}

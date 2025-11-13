package com.openride.booking.exception;

/**
 * Exception thrown when insufficient seats are available
 */
public class InsufficientSeatsException extends RuntimeException {
    
    public InsufficientSeatsException(String message) {
        super(message);
    }
}

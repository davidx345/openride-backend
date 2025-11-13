package com.openride.booking.exception;

/**
 * Exception thrown when seat hold operation fails
 */
public class SeatHoldException extends RuntimeException {
    
    public SeatHoldException(String message) {
        super(message);
    }
    
    public SeatHoldException(String message, Throwable cause) {
        super(message, cause);
    }
}

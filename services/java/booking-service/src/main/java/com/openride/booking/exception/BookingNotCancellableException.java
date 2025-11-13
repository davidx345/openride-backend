package com.openride.booking.exception;

/**
 * Exception thrown when booking cannot be cancelled
 */
public class BookingNotCancellableException extends RuntimeException {
    
    public BookingNotCancellableException(String message) {
        super(message);
    }
}

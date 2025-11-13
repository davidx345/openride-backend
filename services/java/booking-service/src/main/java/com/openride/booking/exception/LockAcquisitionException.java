package com.openride.booking.exception;

/**
 * Exception thrown when distributed lock cannot be acquired
 */
public class LockAcquisitionException extends RuntimeException {
    
    public LockAcquisitionException(String message) {
        super(message);
    }
    
    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}

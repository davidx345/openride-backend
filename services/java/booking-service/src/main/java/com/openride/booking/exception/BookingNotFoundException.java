package com.openride.booking.exception;

import java.util.UUID;

/**
 * Exception thrown when booking is not found
 */
public class BookingNotFoundException extends RuntimeException {
    
    public BookingNotFoundException(UUID bookingId) {
        super("Booking not found: " + bookingId);
    }
    
    public BookingNotFoundException(String bookingReference) {
        super("Booking not found: " + bookingReference);
    }
}

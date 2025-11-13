package com.openride.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main application class for Booking Service
 * 
 * Manages booking and seat inventory operations with distributed locking,
 * state machine transitions, and payment integration.
 * 
 * Port: 8083
 * Context Path: /api
 * 
 * @author OpenRide Platform Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}

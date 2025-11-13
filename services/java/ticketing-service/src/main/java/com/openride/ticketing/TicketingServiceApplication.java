package com.openride.ticketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for OpenRide Ticketing Service.
 * 
 * This service handles:
 * - Cryptographically signed ticket generation
 * - QR code generation and encoding
 * - Blockchain anchoring via Merkle trees
 * - Ticket verification (online and offline)
 * - Public key distribution
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class TicketingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketingServiceApplication.class, args);
    }
}

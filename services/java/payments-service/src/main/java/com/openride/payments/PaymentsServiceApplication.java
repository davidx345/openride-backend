package com.openride.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for OpenRide Payments Service.
 * Handles payment processing with Korapay integration.
 *
 * @author OpenRide Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class PaymentsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentsServiceApplication.class, args);
    }
}

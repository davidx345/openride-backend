package com.openride.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * OpenRide Admin Service Application.
 * 
 * Centralized admin service providing:
 * - Driver verification and KYC management
 * - Booking search and management
 * - Manual refund processing
 * - Dispute resolution
 * - User suspension/ban
 * - System health monitoring
 * - Audit log viewing
 * 
 * @author OpenRide Platform Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableCaching
@EnableScheduling
@ConfigurationPropertiesScan
public class AdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}

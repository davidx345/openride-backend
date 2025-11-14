package com.openride.payouts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Payouts Service.
 * 
 * Manages driver earnings, payouts, and settlement processing.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableKafka
@EnableScheduling
public class PayoutsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayoutsServiceApplication.class, args);
    }
}

package com.openride.payments.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties for payment processing.
 * Binds values from application.yml payment section.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "payment")
public class PaymentConfigProperties {

    /**
     * Payment expiry duration in minutes.
     * After this time, pending payments are automatically marked as failed.
     * Default: 15 minutes
     */
    private Integer expiryMinutes = 15;

    /**
     * Maximum number of retry attempts for failed operations.
     * Default: 3
     */
    private Integer maxRetryAttempts = 3;

    /**
     * Delay between retry attempts in seconds.
     * Default: 5 seconds
     */
    private Integer retryDelaySeconds = 5;

    /**
     * Default currency for payments.
     * Default: NGN (Nigerian Naira)
     */
    private String currency = "NGN";

    /**
     * List of supported payment methods.
     * Options: CARD, BANK_TRANSFER, USSD, MOBILE_MONEY
     */
    private List<String> supportedMethods;
}

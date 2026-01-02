package com.openride.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Dojah KYC verification API.
 * Dojah provides identity verification services in Nigeria.
 * 
 * @see <a href="https://docs.dojah.io/">Dojah API Documentation</a>
 */
@Configuration
@ConfigurationProperties(prefix = "openride.dojah")
@Data
public class DojahProperties {

    /**
     * Dojah App ID from dashboard.
     */
    private String appId;

    /**
     * Dojah Secret Key from dashboard.
     */
    private String secretKey;

    /**
     * Base URL for Dojah API.
     */
    private String baseUrl = "https://api.dojah.io";

    /**
     * Enable test mode - when true, verification is auto-approved.
     * Should be false in production.
     */
    private boolean testMode = true;

    /**
     * Minimum selfie match score required for verification (0.0 - 1.0).
     * Default: 0.70 (70% match)
     */
    private double minSelfieMatchScore = 0.70;

    /**
     * Timeout in seconds for API calls.
     */
    private int timeoutSeconds = 30;
}

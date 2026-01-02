package com.openride.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Termii SMS integration.
 * Termii is a popular SMS gateway in Nigeria.
 * 
 * @see <a href="https://developers.termii.com/">Termii API Documentation</a>
 */
@Configuration
@ConfigurationProperties(prefix = "openride.termii")
@Data
public class TermiiProperties {

    /**
     * Termii API key from dashboard.
     */
    private String apiKey;

    /**
     * Sender ID registered with Termii (e.g., "OpenRide").
     */
    private String senderId = "OpenRide";

    /**
     * Message type: "plain" for regular SMS.
     */
    private String messageType = "plain";

    /**
     * Channel: "generic" for standard SMS, "dnd" for Do-Not-Disturb bypass.
     */
    private String channel = "generic";

    /**
     * Base URL for Termii API.
     */
    private String baseUrl = "https://api.ng.termii.com/api";

    /**
     * Enable test mode - when true, OTP "123456" is always valid.
     * Should be false in production.
     */
    private boolean testMode = true;

    /**
     * Test OTP code that always works in test mode.
     */
    private String testOtpCode = "123456";
}

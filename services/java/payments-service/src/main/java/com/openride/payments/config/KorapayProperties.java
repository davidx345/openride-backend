package com.openride.payments.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties for Korapay payment provider.
 * Binds values from application.yml korapay section.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "korapay")
public class KorapayProperties {

    /**
     * Korapay API base URL.
     * Default: https://api.korapay.com
     */
    private String apiUrl;

    /**
     * Korapay secret key for API authentication.
     * Must be kept secure and never committed to version control.
     */
    private String secretKey;

    /**
     * Korapay public key for client-side integration.
     */
    private String publicKey;

    /**
     * Secret for verifying webhook signatures.
     * Used to validate that webhooks are from Korapay.
     */
    private String webhookSecret;

    /**
     * HTTP request timeout in seconds.
     * Default: 30
     */
    private Integer timeoutSeconds = 30;

    /**
     * Merchant name displayed to customers.
     */
    private String merchantName;
}

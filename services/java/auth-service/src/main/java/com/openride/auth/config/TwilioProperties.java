package com.openride.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Twilio SMS integration.
 */
@Configuration
@ConfigurationProperties(prefix = "openride.twilio")
@Data
public class TwilioProperties {

    private String accountSid;
    private String authToken;
    private String phoneNumber;
}

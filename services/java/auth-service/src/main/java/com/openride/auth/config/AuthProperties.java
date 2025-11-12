package com.openride.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for authentication settings.
 */
@Configuration
@ConfigurationProperties(prefix = "openride.auth")
@Data
public class AuthProperties {

    private OtpConfig otp = new OtpConfig();
    private JwtConfig jwt = new JwtConfig();
    private RateLimitConfig rateLimit = new RateLimitConfig();

    @Data
    public static class OtpConfig {
        private int length = 6;
        private int expirySeconds = 300;
        private int maxAttempts = 5;
    }

    @Data
    public static class JwtConfig {
        private String secret;
        private long accessTokenExpiryMs = 3600000L;
        private long refreshTokenExpiryMs = 604800000L;
    }

    @Data
    public static class RateLimitConfig {
        private int otpSendPerPhone = 3;
        private int otpSendWindowSeconds = 3600;
        private int otpVerifyPerPhone = 10;
        private int otpVerifyWindowSeconds = 3600;
    }
}

package com.openride.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for security settings.
 */
@Configuration
@ConfigurationProperties(prefix = "openride.security")
@Data
public class SecurityProperties {

    private JwtConfig jwt = new JwtConfig();
    private EncryptionConfig encryption = new EncryptionConfig();

    @Data
    public static class JwtConfig {
        private String secret;
    }

    @Data
    public static class EncryptionConfig {
        private String key;
        private String algorithm = "AES/GCM/NoPadding";
    }
}

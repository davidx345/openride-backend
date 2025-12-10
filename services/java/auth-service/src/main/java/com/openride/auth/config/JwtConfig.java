package com.openride.auth.config;

import com.openride.commons.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JWT configuration for auth service.
 * Creates JwtUtil bean with configured settings.
 */
@Configuration
@RequiredArgsConstructor
public class JwtConfig {

    private final AuthProperties authProperties;

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(
            authProperties.getJwt().getSecret(),
            authProperties.getJwt().getAccessTokenExpiryMs() / (60 * 1000), // Convert ms to minutes
            authProperties.getJwt().getRefreshTokenExpiryMs() / (24 * 60 * 60 * 1000) // Convert ms to days
        );
    }
}

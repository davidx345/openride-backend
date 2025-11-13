package com.openride.payments.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for JWT token validation and parsing.
 * Used by JwtAuthenticationFilter to extract user information from tokens.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;

    public JwtTokenProvider(@Value("${spring.security.jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Validates JWT token and checks expiration.
     *
     * @param token JWT token
     * @return true if token is valid
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts user ID from JWT token.
     *
     * @param token JWT token
     * @return user ID
     */
    public UUID getUserId(String token) {
        Claims claims = getClaims(token);
        String userId = claims.getSubject();
        return UUID.fromString(userId);
    }

    /**
     * Extracts user role from JWT token.
     *
     * @param token JWT token
     * @return user role (e.g., "RIDER", "DRIVER", "ADMIN")
     */
    public String getRole(String token) {
        Claims claims = getClaims(token);
        return claims.get("role", String.class);
    }

    /**
     * Extracts phone number from JWT token.
     *
     * @param token JWT token
     * @return phone number
     */
    public String getPhone(String token) {
        Claims claims = getClaims(token);
        return claims.get("phone", String.class);
    }

    /**
     * Checks if token is expired.
     *
     * @param token JWT token
     * @return true if expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Parses JWT token and extracts claims.
     *
     * @param token JWT token
     * @return claims
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}

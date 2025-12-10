package com.openride.commons.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Utility class for JWT token generation and validation.
 * Provides methods to create, parse, and validate JWT tokens.
 */
public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    
    private final SecretKey secretKey;
    private final long accessTokenExpirationMinutes;
    private final long refreshTokenExpirationDays;

    /**
     * Creates a new JwtUtil instance.
     *
     * @param secret                      The secret key for signing tokens
     * @param accessTokenExpirationMinutes  Expiration time for access tokens in minutes
     * @param refreshTokenExpirationDays    Expiration time for refresh tokens in days
     */
    public JwtUtil(String secret, long accessTokenExpirationMinutes, long refreshTokenExpirationDays) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }

    /**
     * Generates an access token for a user.
     *
     * @param userId   The user ID
     * @param phone    The user's phone number
     * @param role     The user's role
     * @return JWT access token
     */
    public String generateAccessToken(String userId, String phone, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("phone", phone);
        claims.put("role", role);
        
        Instant now = Instant.now();
        Instant expiration = now.plus(accessTokenExpirationMinutes, ChronoUnit.MINUTES);
        
        return Jwts.builder()
                .claims(claims)
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Generates a refresh token for a user.
     *
     * @param userId The user ID
     * @return JWT refresh token
     */
    public String generateRefreshToken(String userId) {
        Instant now = Instant.now();
        Instant expiration = now.plus(refreshTokenExpirationDays, ChronoUnit.DAYS);
        
        return Jwts.builder()
                .subject(userId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Extracts the user ID (subject) from a token.
     *
     * @param token The JWT token
     * @return The user ID
     */
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the phone number from a token.
     *
     * @param token The JWT token
     * @return The phone number
     */
    public String extractPhone(String token) {
        return extractClaim(token, claims -> claims.get("phone", String.class));
    }

    /**
     * Extracts the role from a token.
     *
     * @param token The JWT token
     * @return The user role
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * Extracts the expiration date from a token.
     *
     * @param token The JWT token
     * @return The expiration date
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extracts a specific claim from a token.
     *
     * @param token          The JWT token
     * @param claimsResolver Function to extract the claim
     * @param <T>            The type of the claim
     * @return The extracted claim
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extracts all claims from a token.
     *
     * @param token The JWT token
     * @return All claims
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Checks if a token is expired.
     *
     * @param token The JWT token
     * @return true if expired, false otherwise
     */
    public Boolean isTokenExpired(String token) {
        try {
            return extractExpiration(token).before(new Date());
        } catch (Exception e) {
            logger.error("Error checking token expiration", e);
            return true;
        }
    }

    /**
     * Validates a token for a specific user.
     *
     * @param token  The JWT token
     * @param userId The expected user ID
     * @return true if valid, false otherwise
     */
    public Boolean validateToken(String token, String userId) {
        try {
            final String extractedUserId = extractUserId(token);
            return (extractedUserId.equals(userId) && !isTokenExpired(token));
        } catch (Exception e) {
            logger.error("Error validating token", e);
            return false;
        }
    }

    /**
     * Validates a token without checking user ID.
     *
     * @param token The JWT token
     * @return true if valid, false otherwise
     */
    public Boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            logger.error("Error validating token", e);
            return false;
        }
    }

    // ========== Static Utility Methods (for backward compatibility) ==========

    /**
     * Static method to validate a token with a secret key.
     * For backward compatibility with services that use static calls.
     *
     * @param token  The JWT token
     * @param secret The secret key
     * @return true if valid, false otherwise
     */
    public static boolean validateToken(String token, String secret) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Static method to extract subject (user ID) from token.
     * For backward compatibility with services that use static calls.
     *
     * @param token  The JWT token
     * @param secret The secret key
     * @return The subject (user ID)
     */
    public static String extractSubject(String token, String secret) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        } catch (Exception e) {
            logger.error("Error extracting subject from token", e);
            return null;
        }
    }

    /**
     * Static method to extract a specific claim from token.
     * For backward compatibility with services that use static calls.
     *
     * @param token     The JWT token
     * @param claimName The name of the claim to extract
     * @param secret    The secret key
     * @return The claim value as String
     */
    public static String extractClaim(String token, String claimName, String secret) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get(claimName, String.class);
        } catch (Exception e) {
            logger.error("Error extracting claim {} from token", claimName, e);
            return null;
        }
    }
}

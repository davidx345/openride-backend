package com.openride.auth.service;

import com.openride.auth.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

/**
 * Service for rate limiting using Redis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {

    private final RedissonClient redissonClient;
    private final AuthProperties authProperties;

    /**
     * Checks if an OTP send request is allowed for the given phone number.
     *
     * @param phoneNumber phone number to check
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean allowOtpSend(String phoneNumber) {
        String key = "rate_limit:otp_send:" + phoneNumber;
        return checkRateLimit(
            key,
            authProperties.getRateLimit().getOtpSendPerPhone(),
            authProperties.getRateLimit().getOtpSendWindowSeconds()
        );
    }

    /**
     * Checks if an OTP verification request is allowed for the given phone number.
     *
     * @param phoneNumber phone number to check
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean allowOtpVerify(String phoneNumber) {
        String key = "rate_limit:otp_verify:" + phoneNumber;
        return checkRateLimit(
            key,
            authProperties.getRateLimit().getOtpVerifyPerPhone(),
            authProperties.getRateLimit().getOtpVerifyWindowSeconds()
        );
    }

    /**
     * Generic rate limit check.
     *
     * @param key Redis key for rate limiter
     * @param maxRequests maximum number of requests allowed
     * @param windowSeconds time window in seconds
     * @return true if allowed, false if rate limit exceeded
     */
    private boolean checkRateLimit(String key, int maxRequests, int windowSeconds) {
        try {
            RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
            
            if (!rateLimiter.isExists()) {
                rateLimiter.trySetRate(RateType.OVERALL, maxRequests, windowSeconds, RateIntervalUnit.SECONDS);
            }

            boolean allowed = rateLimiter.tryAcquire(1);
            
            if (!allowed) {
                log.warn("Rate limit exceeded for key: {}", key);
            }
            
            return allowed;

        } catch (Exception e) {
            log.error("Error checking rate limit for key {}: {}", key, e.getMessage(), e);
            return true; // Fail open in case of Redis errors
        }
    }
}

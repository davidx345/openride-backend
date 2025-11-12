package com.openride.auth.service;

import com.openride.auth.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimitService.
 * Tests Redis-based distributed rate limiting functionality.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private AuthProperties authProperties;

    @Mock
    private AuthProperties.RateLimitConfig rateLimitConfig;

    @Mock
    private RRateLimiter rateLimiter;

    private RateLimitService rateLimitService;

    private static final String TEST_PHONE = "+2348012345678";

    @BeforeEach
    void setUp() {
        when(authProperties.getRateLimit()).thenReturn(rateLimitConfig);
        when(rateLimitConfig.getOtpSendMaxRequests()).thenReturn(3);
        when(rateLimitConfig.getOtpSendWindowSeconds()).thenReturn(3600);
        when(rateLimitConfig.getOtpVerifyMaxRequests()).thenReturn(10);
        when(rateLimitConfig.getOtpVerifyWindowSeconds()).thenReturn(3600);

        rateLimitService = new RateLimitService(redissonClient, authProperties);
    }

    @Test
    void allowOtpSend_WithinLimit_ReturnsTrue() {
        // Given
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);

        // When
        boolean allowed = rateLimitService.allowOtpSend(TEST_PHONE);

        // Then
        assertThat(allowed).isTrue();
        verify(redissonClient).getRateLimiter("rate_limit:otp_send:" + TEST_PHONE);
        verify(rateLimiter).trySetRate(RateType.OVERALL, 3, 3600, RateIntervalUnit.SECONDS);
        verify(rateLimiter).tryAcquire(1);
    }

    @Test
    void allowOtpSend_ExceedsLimit_ReturnsFalse() {
        // Given
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(1)).thenReturn(false);

        // When
        boolean allowed = rateLimitService.allowOtpSend(TEST_PHONE);

        // Then
        assertThat(allowed).isFalse();
    }

    @Test
    void allowOtpSend_RedisFailure_ReturnsTrue() {
        // Given - fail-open behavior
        when(redissonClient.getRateLimiter(anyString())).thenThrow(new RuntimeException("Redis error"));

        // When
        boolean allowed = rateLimitService.allowOtpSend(TEST_PHONE);

        // Then
        assertThat(allowed).isTrue();
    }

    @Test
    void allowOtpVerify_WithinLimit_ReturnsTrue() {
        // Given
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);

        // When
        boolean allowed = rateLimitService.allowOtpVerify(TEST_PHONE);

        // Then
        assertThat(allowed).isTrue();
        verify(redissonClient).getRateLimiter("rate_limit:otp_verify:" + TEST_PHONE);
        verify(rateLimiter).trySetRate(RateType.OVERALL, 10, 3600, RateIntervalUnit.SECONDS);
        verify(rateLimiter).tryAcquire(1);
    }

    @Test
    void allowOtpVerify_ExceedsLimit_ReturnsFalse() {
        // Given
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(1)).thenReturn(false);

        // When
        boolean allowed = rateLimitService.allowOtpVerify(TEST_PHONE);

        // Then
        assertThat(allowed).isFalse();
    }

    @Test
    void allowOtpVerify_RedisFailure_ReturnsTrue() {
        // Given - fail-open behavior
        when(redissonClient.getRateLimiter(anyString())).thenThrow(new RuntimeException("Redis error"));

        // When
        boolean allowed = rateLimitService.allowOtpVerify(TEST_PHONE);

        // Then
        assertThat(allowed).isTrue();
    }

    @Test
    void allowOtpSend_DifferentPhones_IndependentLimits() {
        // Given
        String phone1 = "+2348012345678";
        String phone2 = "+2348087654321";
        
        RRateLimiter rateLimiter1 = mock(RRateLimiter.class);
        RRateLimiter rateLimiter2 = mock(RRateLimiter.class);
        
        when(redissonClient.getRateLimiter("rate_limit:otp_send:" + phone1)).thenReturn(rateLimiter1);
        when(redissonClient.getRateLimiter("rate_limit:otp_send:" + phone2)).thenReturn(rateLimiter2);
        when(rateLimiter1.tryAcquire(1)).thenReturn(false);
        when(rateLimiter2.tryAcquire(1)).thenReturn(true);

        // When
        boolean allowed1 = rateLimitService.allowOtpSend(phone1);
        boolean allowed2 = rateLimitService.allowOtpSend(phone2);

        // Then
        assertThat(allowed1).isFalse();
        assertThat(allowed2).isTrue();
    }
}

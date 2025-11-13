package com.openride.payments.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IdempotencyService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService Tests")
class IdempotencyServiceTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> rBucket;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(redissonClient);
    }

    @Test
    @DisplayName("Should set payment idempotency key successfully")
    void shouldSetPaymentIdempotencyKeySuccessfully() {
        String idempotencyKey = "test-key-123";
        UUID paymentId = UUID.randomUUID();
        String redisKey = "payment:idempotency:" + idempotencyKey;

        when(redissonClient.getBucket(redisKey)).thenReturn(rBucket);
        when(rBucket.setIfAbsent(paymentId.toString(), 24, TimeUnit.HOURS)).thenReturn(true);

        boolean result = idempotencyService.checkAndSetPaymentIdempotency(idempotencyKey, paymentId);

        assertTrue(result);
        verify(redissonClient).getBucket(redisKey);
        verify(rBucket).setIfAbsent(paymentId.toString(), 24, TimeUnit.HOURS);
    }

    @Test
    @DisplayName("Should return false when idempotency key already exists")
    void shouldReturnFalseWhenIdempotencyKeyAlreadyExists() {
        String idempotencyKey = "test-key-123";
        UUID paymentId = UUID.randomUUID();
        String redisKey = "payment:idempotency:" + idempotencyKey;

        when(redissonClient.getBucket(redisKey)).thenReturn(rBucket);
        when(rBucket.setIfAbsent(paymentId.toString(), 24, TimeUnit.HOURS)).thenReturn(false);

        boolean result = idempotencyService.checkAndSetPaymentIdempotency(idempotencyKey, paymentId);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should get payment ID by idempotency key")
    void shouldGetPaymentIdByIdempotencyKey() {
        String idempotencyKey = "test-key-123";
        UUID expectedPaymentId = UUID.randomUUID();
        String redisKey = "payment:idempotency:" + idempotencyKey;

        when(redissonClient.getBucket(redisKey)).thenReturn(rBucket);
        when(rBucket.get()).thenReturn(expectedPaymentId.toString());

        UUID result = idempotencyService.getPaymentIdByIdempotencyKey(idempotencyKey);

        assertEquals(expectedPaymentId, result);
    }

    @Test
    @DisplayName("Should return null when idempotency key not found")
    void shouldReturnNullWhenIdempotencyKeyNotFound() {
        String idempotencyKey = "test-key-123";
        String redisKey = "payment:idempotency:" + idempotencyKey;

        when(redissonClient.getBucket(redisKey)).thenReturn(rBucket);
        when(rBucket.get()).thenReturn(null);

        UUID result = idempotencyService.getPaymentIdByIdempotencyKey(idempotencyKey);

        assertNull(result);
    }

    @Test
    @DisplayName("Should set webhook processed successfully")
    void shouldSetWebhookProcessedSuccessfully() {
        String reference = "KORAPAY_REF_123";
        String eventType = "charge.success";
        String redisKey = "webhook:processed:" + reference + ":" + eventType;

        when(redissonClient.getBucket(redisKey)).thenReturn(rBucket);
        when(rBucket.setIfAbsent("true", 7, TimeUnit.DAYS)).thenReturn(true);

        boolean result = idempotencyService.checkAndSetWebhookProcessed(reference, eventType);

        assertTrue(result);
        verify(rBucket).setIfAbsent("true", 7, TimeUnit.DAYS);
    }

    @Test
    @DisplayName("Should return false when webhook already processed")
    void shouldReturnFalseWhenWebhookAlreadyProcessed() {
        String reference = "KORAPAY_REF_123";
        String eventType = "charge.success";
        String redisKey = "webhook:processed:" + reference + ":" + eventType;

        when(redissonClient.getBucket(redisKey)).thenReturn(rBucket);
        when(rBucket.setIfAbsent("true", 7, TimeUnit.DAYS)).thenReturn(false);

        boolean result = idempotencyService.checkAndSetWebhookProcessed(reference, eventType);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should clear payment idempotency")
    void shouldClearPaymentIdempotency() {
        String idempotencyKey = "test-key-123";
        String redisKey = "payment:idempotency:" + idempotencyKey;

        when(redissonClient.getBucket(redisKey)).thenReturn(rBucket);
        when(rBucket.delete()).thenReturn(true);

        idempotencyService.clearPaymentIdempotency(idempotencyKey);

        verify(rBucket).delete();
    }

    @Test
    @DisplayName("Should handle invalid UUID in getPaymentIdByIdempotencyKey")
    void shouldHandleInvalidUuidInGetPaymentIdByIdempotencyKey() {
        String idempotencyKey = "test-key-123";
        String redisKey = "payment:idempotency:" + idempotencyKey;

        when(redissonClient.getBucket(redisKey)).thenReturn(rBucket);
        when(rBucket.get()).thenReturn("invalid-uuid");

        UUID result = idempotencyService.getPaymentIdByIdempotencyKey(idempotencyKey);

        assertNull(result);
    }
}

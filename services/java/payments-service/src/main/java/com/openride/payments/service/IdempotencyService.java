package com.openride.payments.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Service for managing idempotency using Redis.
 * Prevents duplicate payment processing from retry requests or webhook replays.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String PAYMENT_IDEMPOTENCY_PREFIX = "payment:idempotency:";
    private static final String WEBHOOK_IDEMPOTENCY_PREFIX = "webhook:processed:";
    private static final Duration PAYMENT_TTL = Duration.ofHours(24);
    private static final Duration WEBHOOK_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    /**
     * Checks if a payment request with the given idempotency key has already been processed.
     * If not, records it atomically.
     *
     * @param idempotencyKey unique key for the payment request
     * @param paymentId payment ID to associate with this request
     * @return true if this is a new request, false if it's a duplicate
     */
    public boolean checkAndSetPaymentIdempotency(String idempotencyKey, UUID paymentId) {
        String key = PAYMENT_IDEMPOTENCY_PREFIX + idempotencyKey;
        Boolean success = redisTemplate.opsForValue()
            .setIfAbsent(key, paymentId.toString(), PAYMENT_TTL);

        if (Boolean.TRUE.equals(success)) {
            log.debug("New payment request: idempotencyKey={}", idempotencyKey);
            return true;
        } else {
            log.warn("Duplicate payment request detected: idempotencyKey={}", idempotencyKey);
            return false;
        }
    }

    /**
     * Gets the payment ID associated with an idempotency key.
     *
     * @param idempotencyKey idempotency key
     * @return payment ID or null if not found
     */
    public UUID getPaymentIdByIdempotencyKey(String idempotencyKey) {
        String key = PAYMENT_IDEMPOTENCY_PREFIX + idempotencyKey;
        String paymentId = redisTemplate.opsForValue().get(key);
        
        if (paymentId != null) {
            return UUID.fromString(paymentId);
        }
        return null;
    }

    /**
     * Checks if a webhook event has already been processed.
     * If not, marks it as processed atomically.
     *
     * @param korapayReference transaction reference from Korapay
     * @param eventType event type (e.g., "charge.success")
     * @return true if this is a new event, false if already processed
     */
    public boolean checkAndSetWebhookProcessed(String korapayReference, String eventType) {
        String key = WEBHOOK_IDEMPOTENCY_PREFIX + korapayReference + ":" + eventType;
        Boolean success = redisTemplate.opsForValue()
            .setIfAbsent(key, String.valueOf(System.currentTimeMillis()), WEBHOOK_TTL);

        if (Boolean.TRUE.equals(success)) {
            log.debug("New webhook event: reference={}, event={}", korapayReference, eventType);
            return true;
        } else {
            log.warn("Duplicate webhook event detected: reference={}, event={}", korapayReference, eventType);
            return false;
        }
    }

    /**
     * Clears payment idempotency record.
     * Used in testing or when explicitly retrying a failed payment.
     *
     * @param idempotencyKey idempotency key to clear
     */
    public void clearPaymentIdempotency(String idempotencyKey) {
        String key = PAYMENT_IDEMPOTENCY_PREFIX + idempotencyKey;
        redisTemplate.delete(key);
        log.debug("Cleared payment idempotency: key={}", idempotencyKey);
    }
}

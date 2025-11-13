package com.openride.payments.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebhookSignatureValidator.
 */
@DisplayName("WebhookSignatureValidator Tests")
class WebhookSignatureValidatorTest {

    private WebhookSignatureValidator validator;
    private static final String TEST_SECRET = "test-webhook-secret-key";

    @BeforeEach
    void setUp() {
        validator = new WebhookSignatureValidator(TEST_SECRET);
    }

    @Test
    @DisplayName("Should validate correct signature")
    void shouldValidateCorrectSignature() {
        String payload = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"TEST_REF\"}}";
        
        // Generate signature using the same algorithm
        String signature = validator.computeHmacSha256(payload, TEST_SECRET);

        assertTrue(validator.validateSignature(payload, signature));
    }

    @Test
    @DisplayName("Should reject invalid signature")
    void shouldRejectInvalidSignature() {
        String payload = "{\"event\":\"charge.success\"}";
        String invalidSignature = "invalid-signature-12345";

        assertFalse(validator.validateSignature(payload, invalidSignature));
    }

    @Test
    @DisplayName("Should reject signature for modified payload")
    void shouldRejectSignatureForModifiedPayload() {
        String originalPayload = "{\"event\":\"charge.success\",\"amount\":5000}";
        String signature = validator.computeHmacSha256(originalPayload, TEST_SECRET);

        String modifiedPayload = "{\"event\":\"charge.success\",\"amount\":10000}";

        assertFalse(validator.validateSignature(modifiedPayload, signature));
    }

    @Test
    @DisplayName("Should handle null payload")
    void shouldHandleNullPayload() {
        assertFalse(validator.validateSignature(null, "some-signature"));
    }

    @Test
    @DisplayName("Should handle null signature")
    void shouldHandleNullSignature() {
        String payload = "{\"event\":\"charge.success\"}";
        assertFalse(validator.validateSignature(payload, null));
    }

    @Test
    @DisplayName("Should handle empty payload")
    void shouldHandleEmptyPayload() {
        assertFalse(validator.validateSignature("", "some-signature"));
    }

    @Test
    @DisplayName("Should handle empty signature")
    void shouldHandleEmptySignature() {
        String payload = "{\"event\":\"charge.success\"}";
        assertFalse(validator.validateSignature(payload, ""));
    }

    @Test
    @DisplayName("Should be case-insensitive for hex signatures")
    void shouldBeCaseInsensitiveForHexSignatures() {
        String payload = "{\"event\":\"charge.success\"}";
        String signature = validator.computeHmacSha256(payload, TEST_SECRET);

        // Test uppercase
        assertTrue(validator.validateSignature(payload, signature.toUpperCase()));
        
        // Test lowercase
        assertTrue(validator.validateSignature(payload, signature.toLowerCase()));
    }

    @Test
    @DisplayName("Should generate consistent signatures")
    void shouldGenerateConsistentSignatures() {
        String payload = "{\"event\":\"charge.success\"}";

        String signature1 = validator.computeHmacSha256(payload, TEST_SECRET);
        String signature2 = validator.computeHmacSha256(payload, TEST_SECRET);

        assertEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should generate different signatures for different secrets")
    void shouldGenerateDifferentSignaturesForDifferentSecrets() {
        String payload = "{\"event\":\"charge.success\"}";
        String secret1 = "secret-1";
        String secret2 = "secret-2";

        String signature1 = validator.computeHmacSha256(payload, secret1);
        String signature2 = validator.computeHmacSha256(payload, secret2);

        assertNotEquals(signature1, signature2);
    }

    @Test
    @DisplayName("Should generate different signatures for different payloads")
    void shouldGenerateDifferentSignaturesForDifferentPayloads() {
        String payload1 = "{\"event\":\"charge.success\"}";
        String payload2 = "{\"event\":\"charge.failed\"}";

        String signature1 = validator.computeHmacSha256(payload1, TEST_SECRET);
        String signature2 = validator.computeHmacSha256(payload2, TEST_SECRET);

        assertNotEquals(signature1, signature2);
    }
}

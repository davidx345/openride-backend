package com.openride.payments.webhook;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Validates webhook signatures from Korapay.
 * Uses HMAC-SHA256 to verify webhook authenticity.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookSignatureValidator {

    private static final String HMAC_SHA256 = "HmacSHA256";

    @Value("${korapay.webhook-secret}")
    private String webhookSecret;

    /**
     * Validates webhook signature.
     *
     * @param payload webhook payload as string
     * @param signature signature from X-Korapay-Signature header
     * @return true if signature is valid
     */
    public boolean validateSignature(String payload, String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("Webhook signature is missing");
            return false;
        }

        try {
            String computedSignature = computeHmacSha256(payload, webhookSecret);
            boolean isValid = computedSignature.equalsIgnoreCase(signature);

            if (!isValid) {
                log.warn("Webhook signature mismatch. Expected: {}, Got: {}", computedSignature, signature);
            }

            return isValid;

        } catch (Exception e) {
            log.error("Error validating webhook signature", e);
            return false;
        }
    }

    /**
     * Computes HMAC-SHA256 signature.
     *
     * @param data data to sign
     * @param secret secret key
     * @return hex-encoded signature
     */
    private String computeHmacSha256(String data, String secret) 
            throws NoSuchAlgorithmException, InvalidKeyException {
        
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8), 
            HMAC_SHA256
        );
        mac.init(secretKeySpec);
        
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmacBytes);
    }
}

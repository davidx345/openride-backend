package com.openride.payments.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openride.payments.webhook.WebhookSignatureValidator;
import com.openride.payments.webhook.WebhookService;
import com.openride.payments.webhook.dto.KorapayWebhookPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for handling Korapay webhooks.
 * Receives payment status notifications from Korapay.
 */
@Slf4j
@RestController
@RequestMapping("/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Webhook endpoints for payment notifications")
public class WebhookController {

    private static final String SIGNATURE_HEADER = "X-Korapay-Signature";

    private final WebhookService webhookService;
    private final WebhookSignatureValidator signatureValidator;
    private final ObjectMapper objectMapper;

    /**
     * Receives webhook notifications from Korapay.
     *
     * @param signature webhook signature for verification
     * @param payload webhook payload
     * @return 200 OK if processed, 400 if invalid signature
     */
    @PostMapping("/korapay")
    @Operation(summary = "Receive Korapay webhook", 
               description = "Endpoint for Korapay to send payment status notifications")
    public ResponseEntity<Map<String, String>> handleKorapayWebhook(
            @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody String payload
    ) {
        log.info("Received Korapay webhook");

        try {
            // Validate signature
            if (!signatureValidator.validateSignature(payload, signature)) {
                log.error("Invalid webhook signature");
                return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("status", "error", "message", "Invalid signature"));
            }

            // Parse payload
            KorapayWebhookPayload webhookPayload = objectMapper.readValue(
                payload, 
                KorapayWebhookPayload.class
            );

            // Process webhook
            boolean processed = webhookService.processWebhook(webhookPayload);

            if (processed) {
                return ResponseEntity.ok(Map.of("status", "processed"));
            } else {
                return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("status", "error", "message", "Failed to process webhook"));
            }

        } catch (Exception e) {
            log.error("Error handling webhook", e);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}

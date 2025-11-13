package com.openride.payments.webhook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO for Korapay webhook payload.
 * Korapay sends webhooks for payment status updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KorapayWebhookPayload {

    /**
     * Event type (e.g., "charge.success", "charge.failed").
     */
    @JsonProperty("event")
    private String event;

    /**
     * Event data containing transaction details.
     */
    @JsonProperty("data")
    private EventData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventData {

        @JsonProperty("reference")
        private String reference;

        @JsonProperty("amount")
        private Long amount;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("status")
        private String status;

        @JsonProperty("payment_method")
        private String paymentMethod;

        @JsonProperty("transaction_reference")
        private String transactionReference;

        @JsonProperty("customer")
        private CustomerData customer;

        @JsonProperty("metadata")
        private Map<String, String> metadata;

        @JsonProperty("paid_at")
        private String paidAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerData {

        @JsonProperty("name")
        private String name;

        @JsonProperty("email")
        private String email;
    }

    /**
     * Checks if the event represents a successful payment.
     */
    public boolean isSuccessEvent() {
        return "charge.success".equalsIgnoreCase(event);
    }

    /**
     * Checks if the event represents a failed payment.
     */
    public boolean isFailedEvent() {
        return "charge.failed".equalsIgnoreCase(event);
    }

    /**
     * Gets the transaction reference.
     */
    public String getReference() {
        return data != null ? data.getReference() : null;
    }

    /**
     * Gets the transaction ID.
     */
    public String getTransactionReference() {
        return data != null ? data.getTransactionReference() : null;
    }

    /**
     * Gets the payment method.
     */
    public String getPaymentMethod() {
        return data != null ? data.getPaymentMethod() : null;
    }
}

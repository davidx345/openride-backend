package com.openride.payments.korapay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO from Korapay charge verification.
 * Returned from GET /merchant/api/v1/charges/{reference} endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KorapayVerifyResponse {

    @JsonProperty("status")
    private Boolean status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private VerificationData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VerificationData {

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

        @JsonProperty("paid_at")
        private String paidAt;

        @JsonProperty("created_at")
        private String createdAt;
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
     * Checks if the request was successful.
     */
    public boolean isSuccessful() {
        return Boolean.TRUE.equals(status);
    }

    /**
     * Checks if payment is successful.
     */
    public boolean isPaymentSuccessful() {
        return data != null && "success".equalsIgnoreCase(data.getStatus());
    }

    /**
     * Gets payment status.
     */
    public String getPaymentStatus() {
        return data != null ? data.getStatus() : null;
    }

    /**
     * Gets transaction reference.
     */
    public String getTransactionReference() {
        return data != null ? data.getTransactionReference() : null;
    }

    /**
     * Gets payment method used.
     */
    public String getPaymentMethod() {
        return data != null ? data.getPaymentMethod() : null;
    }
}

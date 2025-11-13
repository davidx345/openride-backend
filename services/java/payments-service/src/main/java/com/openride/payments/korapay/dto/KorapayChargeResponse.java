package com.openride.payments.korapay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO from Korapay charge initialization.
 * Returned from /merchant/api/v1/charges/initialize endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KorapayChargeResponse {

    @JsonProperty("status")
    private Boolean status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private ChargeData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChargeData {

        @JsonProperty("reference")
        private String reference;

        @JsonProperty("checkout_url")
        private String checkoutUrl;

        @JsonProperty("amount")
        private Long amount;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("status")
        private String status;

        @JsonProperty("transaction_reference")
        private String transactionReference;
    }

    /**
     * Checks if the request was successful.
     */
    public boolean isSuccessful() {
        return Boolean.TRUE.equals(status);
    }

    /**
     * Gets the checkout URL for customer redirect.
     */
    public String getCheckoutUrl() {
        return data != null ? data.getCheckoutUrl() : null;
    }

    /**
     * Gets the transaction reference.
     */
    public String getReference() {
        return data != null ? data.getReference() : null;
    }
}

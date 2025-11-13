package com.openride.payments.korapay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request DTO for initializing a payment with Korapay.
 * Maps to Korapay's /merchant/api/v1/charges/initialize endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KorapayChargeRequest {

    /**
     * Unique reference for this transaction.
     * Used for idempotency and tracking.
     */
    @JsonProperty("reference")
    private String reference;

    /**
     * Amount to charge in the smallest currency unit (kobo for NGN).
     * Example: 150000 = 1500.00 NGN
     */
    @JsonProperty("amount")
    private Long amount;

    /**
     * Currency code (NGN, USD, etc.).
     */
    @JsonProperty("currency")
    private String currency;

    /**
     * Customer's email address.
     */
    @JsonProperty("customer")
    private CustomerInfo customer;

    /**
     * Merchant name to display.
     */
    @JsonProperty("merchant_bears_cost")
    private Boolean merchantBearsCost;

    /**
     * Redirect URL after payment completion.
     */
    @JsonProperty("redirect_url")
    private String redirectUrl;

    /**
     * Notification URL for webhook callbacks.
     */
    @JsonProperty("notification_url")
    private String notificationUrl;

    /**
     * Additional metadata.
     */
    @JsonProperty("metadata")
    private Map<String, String> metadata;

    /**
     * Payment channels to enable.
     */
    @JsonProperty("channels")
    private String[] channels;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {
        @JsonProperty("name")
        private String name;

        @JsonProperty("email")
        private String email;
    }

    /**
     * Converts amount from NGN to kobo (smallest unit).
     * Korapay requires amount in kobo.
     */
    public static Long toKobo(BigDecimal amountInNGN) {
        return amountInNGN.multiply(BigDecimal.valueOf(100)).longValue();
    }

    /**
     * Converts amount from kobo to NGN.
     */
    public static BigDecimal fromKobo(Long amountInKobo) {
        return BigDecimal.valueOf(amountInKobo).divide(BigDecimal.valueOf(100));
    }
}

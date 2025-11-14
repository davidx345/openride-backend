package com.openride.payouts.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Factory for selecting payment provider.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProviderFactory {

    private final PaystackProvider paystackProvider;
    // private final FlutterwaveProvider flutterwaveProvider; // Future implementation

    @Value("${app.payment.default-provider:PAYSTACK}")
    private String defaultProvider;

    /**
     * Get payment provider instance.
     *
     * @return Payment provider implementation
     */
    public PaymentProvider getProvider() {
        return getProvider(defaultProvider);
    }

    /**
     * Get specific payment provider.
     *
     * @param providerName Provider name (PAYSTACK, FLUTTERWAVE)
     * @return Payment provider implementation
     */
    public PaymentProvider getProvider(String providerName) {
        log.debug("Getting payment provider: {}", providerName);

        switch (providerName.toUpperCase()) {
            case "PAYSTACK":
                return paystackProvider;
            // case "FLUTTERWAVE":
            //     return flutterwaveProvider;
            default:
                log.warn("Unknown payment provider: {}, falling back to Paystack", providerName);
                return paystackProvider;
        }
    }
}

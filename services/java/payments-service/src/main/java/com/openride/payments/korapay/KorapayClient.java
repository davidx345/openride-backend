package com.openride.payments.korapay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openride.payments.config.KorapayProperties;
import com.openride.payments.exception.PaymentException;
import com.openride.payments.korapay.dto.KorapayChargeRequest;
import com.openride.payments.korapay.dto.KorapayChargeResponse;
import com.openride.payments.korapay.dto.KorapayVerifyResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Client for interacting with Korapay payment API.
 * Handles charge initialization, verification, and refunds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KorapayClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final KorapayProperties korapayProperties;
    private final ObjectMapper objectMapper;
    private OkHttpClient httpClient;

    /**
     * Initializes the HTTP client with configured timeout.
     */
    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(korapayProperties.getTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(korapayProperties.getTimeoutSeconds()))
                .writeTimeout(Duration.ofSeconds(korapayProperties.getTimeoutSeconds()))
                .build();
        }
        return httpClient;
    }

    /**
     * Initializes a payment charge with Korapay.
     *
     * @param request charge request
     * @return charge response with checkout URL
     * @throws PaymentException if initialization fails
     */
    public KorapayChargeResponse initializeCharge(KorapayChargeRequest request) {
        String url = korapayProperties.getApiUrl() + "/merchant/api/v1/charges/initialize";

        try {
            String jsonBody = objectMapper.writeValueAsString(request);

            Request httpRequest = new Request.Builder()
                .url(url)
                .addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + korapayProperties.getSecretKey())
                .post(RequestBody.create(jsonBody, JSON))
                .build();

            log.info("Initializing Korapay charge: reference={}", request.getReference());

            try (Response response = getHttpClient().newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    log.error("Korapay charge initialization failed: status={}, body={}", 
                        response.code(), responseBody);
                    throw new PaymentException("Failed to initialize payment: " + responseBody);
                }

                KorapayChargeResponse chargeResponse = objectMapper.readValue(
                    responseBody, 
                    KorapayChargeResponse.class
                );

                if (!chargeResponse.isSuccessful()) {
                    throw new PaymentException("Korapay returned error: " + chargeResponse.getMessage());
                }

                log.info("Korapay charge initialized successfully: reference={}", request.getReference());
                return chargeResponse;
            }
        } catch (IOException e) {
            log.error("Error initializing Korapay charge", e);
            throw new PaymentException("Network error while initializing payment", e);
        }
    }

    /**
     * Verifies a payment transaction with Korapay.
     *
     * @param reference transaction reference
     * @return verification response
     * @throws PaymentException if verification fails
     */
    public KorapayVerifyResponse verifyCharge(String reference) {
        String url = korapayProperties.getApiUrl() + "/merchant/api/v1/charges/" + reference;

        try {
            Request httpRequest = new Request.Builder()
                .url(url)
                .addHeader(AUTHORIZATION_HEADER, BEARER_PREFIX + korapayProperties.getSecretKey())
                .get()
                .build();

            log.info("Verifying Korapay charge: reference={}", reference);

            try (Response response = getHttpClient().newCall(httpRequest).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    log.error("Korapay charge verification failed: status={}, body={}", 
                        response.code(), responseBody);
                    throw new PaymentException("Failed to verify payment: " + responseBody);
                }

                KorapayVerifyResponse verifyResponse = objectMapper.readValue(
                    responseBody, 
                    KorapayVerifyResponse.class
                );

                if (!verifyResponse.isSuccessful()) {
                    throw new PaymentException("Korapay verification error: " + verifyResponse.getMessage());
                }

                log.info("Korapay charge verified: reference={}, status={}", 
                    reference, verifyResponse.getPaymentStatus());
                return verifyResponse;
            }
        } catch (IOException e) {
            log.error("Error verifying Korapay charge", e);
            throw new PaymentException("Network error while verifying payment", e);
        }
    }

    /**
     * Queries transaction details from Korapay.
     * Used for reconciliation.
     *
     * @param reference transaction reference
     * @return verification response with transaction details
     */
    public KorapayVerifyResponse queryTransaction(String reference) {
        return verifyCharge(reference);
    }
}

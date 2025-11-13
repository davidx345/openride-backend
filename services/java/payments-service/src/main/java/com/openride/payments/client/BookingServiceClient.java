package com.openride.payments.client;

import com.openride.payments.exception.PaymentException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

/**
 * REST client for Booking Service integration.
 * Confirms or cancels bookings based on payment status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Value("${booking-service.url}")
    private String bookingServiceUrl;

    @Value("${booking-service.confirm-endpoint}")
    private String confirmEndpoint;

    @Value("${booking-service.cancel-endpoint}")
    private String cancelEndpoint;

    @Value("${booking-service.timeout-seconds:10}")
    private Integer timeoutSeconds;

    @Value("${booking-service.max-retries:3}")
    private Integer maxRetries;

    @Value("${booking-service.retry-delay-seconds:2}")
    private Integer retryDelaySeconds;

    private OkHttpClient httpClient;

    /**
     * Initializes HTTP client with timeout configuration.
     */
    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .readTimeout(Duration.ofSeconds(timeoutSeconds))
                .writeTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        }
        return httpClient;
    }

    /**
     * Confirms a booking after successful payment.
     *
     * @param bookingId booking ID
     * @param paymentId payment ID
     * @throws PaymentException if confirmation fails after retries
     */
    public void confirmBooking(UUID bookingId, UUID paymentId) {
        String url = bookingServiceUrl + confirmEndpoint.replace("{bookingId}", bookingId.toString());
        String requestBody = String.format("{\"paymentId\":\"%s\"}", paymentId);

        log.info("Confirming booking: bookingId={}, paymentId={}", bookingId, paymentId);

        executeWithRetry(url, requestBody, "confirm", bookingId);
    }

    /**
     * Cancels a booking after payment failure.
     *
     * @param bookingId booking ID
     * @param reason cancellation reason
     */
    public void cancelBooking(UUID bookingId, String reason) {
        String url = bookingServiceUrl + cancelEndpoint.replace("{bookingId}", bookingId.toString());
        String requestBody = String.format("{\"reason\":\"%s\"}", reason);

        log.info("Cancelling booking: bookingId={}, reason={}", bookingId, reason);

        try {
            executeWithRetry(url, requestBody, "cancel", bookingId);
        } catch (Exception e) {
            // Don't throw exception for cancel failures - just log
            log.error("Failed to cancel booking: bookingId={}", bookingId, e);
        }
    }

    /**
     * Executes HTTP request with exponential backoff retry.
     */
    private void executeWithRetry(String url, String requestBody, String operation, UUID bookingId) {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < maxRetries) {
            attempt++;

            try {
                Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestBody, JSON))
                    .build();

                try (Response response = getHttpClient().newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        log.info("Booking {} succeeded: bookingId={}, attempt={}", 
                            operation, bookingId, attempt);
                        return;
                    } else {
                        String responseBody = response.body() != null ? response.body().string() : "";
                        log.warn("Booking {} failed: bookingId={}, status={}, body={}, attempt={}/{}", 
                            operation, bookingId, response.code(), responseBody, attempt, maxRetries);
                        
                        if (response.code() >= 400 && response.code() < 500 && response.code() != 429) {
                            // Client error (except rate limit) - no point retrying
                            throw new PaymentException(
                                "Booking " + operation + " failed with client error: " + response.code());
                        }
                    }
                }

            } catch (IOException e) {
                log.error("Network error during booking {}: bookingId={}, attempt={}/{}", 
                    operation, bookingId, attempt, maxRetries, e);
                lastException = e;
            }

            // Wait before retry (exponential backoff)
            if (attempt < maxRetries) {
                try {
                    long delayMs = (long) retryDelaySeconds * 1000 * attempt;
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new PaymentException("Retry interrupted", ie);
                }
            }
        }

        throw new PaymentException(
            "Failed to " + operation + " booking after " + maxRetries + " attempts", 
            lastException
        );
    }
}

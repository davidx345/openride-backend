package com.openride.payments.webhook;

import com.openride.payments.client.BookingServiceClient;
import com.openride.payments.model.Payment;
import com.openride.payments.service.IdempotencyService;
import com.openride.payments.service.PaymentService;
import com.openride.payments.webhook.dto.KorapayWebhookPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for processing Korapay webhook events.
 * Handles payment confirmation and booking updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final BookingServiceClient bookingServiceClient;

    /**
     * Processes a webhook event from Korapay.
     *
     * @param payload webhook payload
     * @return true if processed successfully
     */
    public boolean processWebhook(KorapayWebhookPayload payload) {
        String reference = payload.getReference();
        String eventType = payload.getEvent();

        log.info("Processing webhook: event={}, reference={}", eventType, reference);

        // Check idempotency - prevent duplicate processing
        if (!idempotencyService.checkAndSetWebhookProcessed(reference, eventType)) {
            log.warn("Webhook already processed: event={}, reference={}", eventType, reference);
            return true; // Return true to acknowledge receipt
        }

        try {
            if (payload.isSuccessEvent()) {
                return processSuccessEvent(payload);
            } else if (payload.isFailedEvent()) {
                return processFailedEvent(payload);
            } else {
                log.info("Ignoring webhook event: type={}", eventType);
                return true;
            }

        } catch (Exception e) {
            log.error("Error processing webhook: event={}, reference={}", eventType, reference, e);
            return false;
        }
    }

    /**
     * Processes successful payment event.
     */
    private boolean processSuccessEvent(KorapayWebhookPayload payload) {
        String reference = payload.getReference();
        String transactionId = payload.getTransactionReference();
        String paymentMethod = payload.getPaymentMethod();

        log.info("Processing success event: reference={}, txnId={}", reference, transactionId);

        try {
            // Confirm payment
            Payment payment = paymentService.confirmPayment(reference, transactionId, paymentMethod);

            // Confirm booking
            try {
                bookingServiceClient.confirmBooking(payment.getBookingId(), payment.getId());
                log.info("Booking confirmed: bookingId={}, paymentId={}", 
                    payment.getBookingId(), payment.getId());
            } catch (Exception e) {
                log.error("Failed to confirm booking but payment is successful: paymentId={}, bookingId={}", 
                    payment.getId(), payment.getBookingId(), e);
                // Payment is successful, so we return true
                // Booking confirmation will be retried by reconciliation job
            }

            return true;

        } catch (Exception e) {
            log.error("Error processing success event: reference={}", reference, e);
            return false;
        }
    }

    /**
     * Processes failed payment event.
     */
    private boolean processFailedEvent(KorapayWebhookPayload payload) {
        String reference = payload.getReference();

        log.info("Processing failed event: reference={}", reference);

        try {
            Payment payment = paymentService.getPayment(
                paymentService.getPaymentByBookingId(
                    paymentService.getPayment(null).getBookingId()
                ).getId()
            );

            // Cancel booking
            try {
                bookingServiceClient.cancelBooking(
                    payment.getBookingId(), 
                    "Payment failed"
                );
                log.info("Booking cancelled due to payment failure: bookingId={}", payment.getBookingId());
            } catch (Exception e) {
                log.error("Failed to cancel booking: bookingId={}", payment.getBookingId(), e);
            }

            return true;

        } catch (Exception e) {
            log.error("Error processing failed event: reference={}", reference, e);
            return false;
        }
    }
}

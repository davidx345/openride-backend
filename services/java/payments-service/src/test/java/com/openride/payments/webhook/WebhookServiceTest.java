package com.openride.payments.webhook;

import com.openride.payments.korapay.dto.KorapayWebhookPayload;
import com.openride.payments.model.Payment;
import com.openride.payments.model.PaymentStatus;
import com.openride.payments.service.BookingServiceClient;
import com.openride.payments.service.IdempotencyService;
import com.openride.payments.service.PaymentService;
import com.openride.payments.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebhookService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookService Tests")
class WebhookServiceTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private BookingServiceClient bookingServiceClient;

    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(
            paymentService,
            idempotencyService,
            bookingServiceClient
        );
    }

    @Test
    @DisplayName("Should process success webhook successfully")
    void shouldProcessSuccessWebhookSuccessfully() {
        KorapayWebhookPayload payload = createSuccessPayload();
        String reference = "TEST_REF_123";

        when(idempotencyService.checkAndSetWebhookProcessed(eq(reference), eq("charge.success")))
            .thenReturn(true);

        Payment payment = createPayment(PaymentStatus.SUCCESS);
        when(paymentService.confirmPayment(eq(reference), anyString(), anyString()))
            .thenReturn(payment);

        boolean result = webhookService.processWebhook(payload);

        assertTrue(result);
        verify(paymentService).confirmPayment(eq(reference), anyString(), anyString());
        verify(bookingServiceClient).confirmBooking(any(UUID.class));
    }

    @Test
    @DisplayName("Should skip already processed webhook")
    void shouldSkipAlreadyProcessedWebhook() {
        KorapayWebhookPayload payload = createSuccessPayload();
        String reference = "TEST_REF_123";

        when(idempotencyService.checkAndSetWebhookProcessed(eq(reference), eq("charge.success")))
            .thenReturn(false);

        boolean result = webhookService.processWebhook(payload);

        assertTrue(result);
        verify(paymentService, never()).confirmPayment(anyString(), anyString(), anyString());
        verify(bookingServiceClient, never()).confirmBooking(any());
    }

    @Test
    @DisplayName("Should process failed webhook")
    void shouldProcessFailedWebhook() {
        KorapayWebhookPayload payload = createFailedPayload();
        String reference = "TEST_REF_123";

        when(idempotencyService.checkAndSetWebhookProcessed(eq(reference), eq("charge.failed")))
            .thenReturn(true);

        Payment payment = createPayment(PaymentStatus.FAILED);
        when(paymentService.confirmPayment(eq(reference), anyString(), anyString()))
            .thenReturn(payment);

        boolean result = webhookService.processWebhook(payload);

        assertTrue(result);
        verify(bookingServiceClient).cancelBooking(any(UUID.class));
    }

    @Test
    @DisplayName("Should return true even if booking confirmation fails")
    void shouldReturnTrueEvenIfBookingConfirmationFails() {
        KorapayWebhookPayload payload = createSuccessPayload();
        String reference = "TEST_REF_123";

        when(idempotencyService.checkAndSetWebhookProcessed(eq(reference), eq("charge.success")))
            .thenReturn(true);

        Payment payment = createPayment(PaymentStatus.SUCCESS);
        when(paymentService.confirmPayment(eq(reference), anyString(), anyString()))
            .thenReturn(payment);

        doThrow(new RuntimeException("Booking service error"))
            .when(bookingServiceClient).confirmBooking(any(UUID.class));

        boolean result = webhookService.processWebhook(payload);

        assertTrue(result);
        verify(paymentService).confirmPayment(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle unknown event type")
    void shouldHandleUnknownEventType() {
        KorapayWebhookPayload payload = new KorapayWebhookPayload();
        payload.setEvent("charge.unknown");
        
        KorapayWebhookPayload.EventData data = new KorapayWebhookPayload.EventData();
        data.setReference("TEST_REF_123");
        payload.setData(data);

        when(idempotencyService.checkAndSetWebhookProcessed(anyString(), anyString()))
            .thenReturn(true);

        boolean result = webhookService.processWebhook(payload);

        assertTrue(result);
        verify(paymentService, never()).confirmPayment(anyString(), anyString(), anyString());
    }

    private KorapayWebhookPayload createSuccessPayload() {
        KorapayWebhookPayload payload = new KorapayWebhookPayload();
        payload.setEvent("charge.success");

        KorapayWebhookPayload.EventData data = new KorapayWebhookPayload.EventData();
        data.setReference("TEST_REF_123");
        data.setAmount(500000L); // 5000.00 NGN in kobo
        data.setCurrency("NGN");
        data.setStatus("success");
        data.setTransactionReference("TXN_123");
        data.setPaymentMethod("card");

        KorapayWebhookPayload.Customer customer = new KorapayWebhookPayload.Customer();
        customer.setName("Test User");
        customer.setEmail("test@example.com");
        data.setCustomer(customer);

        payload.setData(data);
        return payload;
    }

    private KorapayWebhookPayload createFailedPayload() {
        KorapayWebhookPayload payload = new KorapayWebhookPayload();
        payload.setEvent("charge.failed");

        KorapayWebhookPayload.EventData data = new KorapayWebhookPayload.EventData();
        data.setReference("TEST_REF_123");
        data.setStatus("failed");
        payload.setData(data);

        return payload;
    }

    private Payment createPayment(PaymentStatus status) {
        return Payment.builder()
            .id(UUID.randomUUID())
            .bookingId(UUID.randomUUID())
            .riderId(UUID.randomUUID())
            .amount(new BigDecimal("5000.00"))
            .currency("NGN")
            .status(status)
            .korapayReference("TEST_REF_123")
            .idempotencyKey("test-key-123")
            .initiatedAt(LocalDateTime.now())
            .build();
    }
}

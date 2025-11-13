package com.openride.payments.service;

import com.openride.payments.config.PaymentConfigProperties;
import com.openride.payments.exception.DuplicatePaymentException;
import com.openride.payments.exception.PaymentException;
import com.openride.payments.exception.PaymentNotFoundException;
import com.openride.payments.korapay.KorapayClient;
import com.openride.payments.korapay.dto.KorapayChargeResponse;
import com.openride.payments.model.Payment;
import com.openride.payments.model.PaymentMethod;
import com.openride.payments.model.PaymentStatus;
import com.openride.payments.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventService paymentEventService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private KorapayClient korapayClient;

    @Mock
    private PaymentStateMachine stateMachine;

    @Mock
    private PaymentConfigProperties paymentConfig;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
            paymentRepository,
            paymentEventService,
            idempotencyService,
            korapayClient,
            stateMachine,
            paymentConfig
        );

        when(paymentConfig.getCurrency()).thenReturn("NGN");
        when(paymentConfig.getExpiryMinutes()).thenReturn(15);
    }

    @Test
    @DisplayName("Should initiate payment successfully")
    void shouldInitiatePaymentSuccessfully() {
        UUID bookingId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("5000.00");
        String email = "test@example.com";
        String name = "Test User";
        String idempotencyKey = "test-key-123";

        when(idempotencyService.getPaymentIdByIdempotencyKey(idempotencyKey)).thenReturn(null);
        when(paymentRepository.existsByBookingId(bookingId)).thenReturn(false);
        
        Payment savedPayment = createPayment(PaymentStatus.INITIATED);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        KorapayChargeResponse chargeResponse = new KorapayChargeResponse();
        chargeResponse.setCheckoutUrl("https://checkout.korapay.com/test");
        when(korapayClient.initializeCharge(any())).thenReturn(chargeResponse);

        Payment result = paymentService.initiatePayment(
            bookingId, riderId, amount, email, name, idempotencyKey
        );

        assertNotNull(result);
        verify(idempotencyService).checkAndSetPaymentIdempotency(eq(idempotencyKey), any(UUID.class));
        verify(korapayClient).initializeCharge(any());
        verify(stateMachine).transition(any(Payment.class), eq(PaymentStatus.PENDING));
        verify(paymentEventService, times(2)).logEvent(any(), any(), anyString());
    }

    @Test
    @DisplayName("Should throw DuplicatePaymentException when idempotency key exists")
    void shouldThrowDuplicatePaymentExceptionWhenIdempotencyKeyExists() {
        UUID bookingId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("5000.00");
        String idempotencyKey = "test-key-123";
        UUID existingPaymentId = UUID.randomUUID();

        when(idempotencyService.getPaymentIdByIdempotencyKey(idempotencyKey))
            .thenReturn(existingPaymentId);
        
        Payment existingPayment = createPayment(PaymentStatus.PENDING);
        when(paymentRepository.findById(existingPaymentId)).thenReturn(Optional.of(existingPayment));

        assertThrows(DuplicatePaymentException.class, () -> {
            paymentService.initiatePayment(
                bookingId, riderId, amount, "test@example.com", "Test User", idempotencyKey
            );
        });

        verify(paymentRepository, never()).save(any());
        verify(korapayClient, never()).initializeCharge(any());
    }

    @Test
    @DisplayName("Should throw DuplicatePaymentException when booking already has payment")
    void shouldThrowDuplicatePaymentExceptionWhenBookingAlreadyHasPayment() {
        UUID bookingId = UUID.randomUUID();
        UUID riderId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("5000.00");
        String idempotencyKey = "test-key-123";

        when(idempotencyService.getPaymentIdByIdempotencyKey(idempotencyKey)).thenReturn(null);
        when(paymentRepository.existsByBookingId(bookingId)).thenReturn(true);

        assertThrows(DuplicatePaymentException.class, () -> {
            paymentService.initiatePayment(
                bookingId, riderId, amount, "test@example.com", "Test User", idempotencyKey
            );
        });

        verify(korapayClient, never()).initializeCharge(any());
    }

    @Test
    @DisplayName("Should confirm payment successfully")
    void shouldConfirmPaymentSuccessfully() {
        String korapayReference = "KORAPAY_REF_123";
        String transactionId = "TXN_123";
        String paymentMethod = "card";

        Payment payment = createPayment(PaymentStatus.PENDING);
        when(paymentRepository.findByKorapayReference(korapayReference))
            .thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.confirmPayment(korapayReference, transactionId, paymentMethod);

        assertNotNull(result);
        verify(paymentRepository).save(any(Payment.class));
        verify(paymentEventService).logEvent(any(), any(), eq("PAYMENT_CONFIRMED"));
    }

    @Test
    @DisplayName("Should not confirm already confirmed payment")
    void shouldNotConfirmAlreadyConfirmedPayment() {
        String korapayReference = "KORAPAY_REF_123";
        String transactionId = "TXN_123";

        Payment payment = createPayment(PaymentStatus.SUCCESS);
        when(paymentRepository.findByKorapayReference(korapayReference))
            .thenReturn(Optional.of(payment));

        Payment result = paymentService.confirmPayment(korapayReference, transactionId, "card");

        assertEquals(PaymentStatus.SUCCESS, result.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw PaymentNotFoundException when confirming non-existent payment")
    void shouldThrowPaymentNotFoundExceptionWhenConfirmingNonExistentPayment() {
        String korapayReference = "KORAPAY_REF_123";

        when(paymentRepository.findByKorapayReference(korapayReference))
            .thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> {
            paymentService.confirmPayment(korapayReference, "TXN_123", "card");
        });
    }

    @Test
    @DisplayName("Should process refund successfully")
    void shouldProcessRefundSuccessfully() {
        UUID paymentId = UUID.randomUUID();
        BigDecimal refundAmount = new BigDecimal("2500.00");
        String reason = "Service not rendered";

        Payment payment = createPayment(PaymentStatus.SUCCESS);
        payment.setAmount(new BigDecimal("5000.00"));
        
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.processRefund(paymentId, refundAmount, reason);

        assertNotNull(result);
        assertEquals(PaymentStatus.REFUNDED, result.getStatus());
        assertEquals(refundAmount, result.getRefundAmount());
        assertEquals(reason, result.getRefundReason());
        verify(paymentEventService).logEvent(any(), any(), eq("PAYMENT_REFUNDED"));
    }

    @Test
    @DisplayName("Should process full refund when amount is null")
    void shouldProcessFullRefundWhenAmountIsNull() {
        UUID paymentId = UUID.randomUUID();
        String reason = "Full refund requested";

        Payment payment = createPayment(PaymentStatus.SUCCESS);
        BigDecimal paymentAmount = new BigDecimal("5000.00");
        payment.setAmount(paymentAmount);
        
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.processRefund(paymentId, null, reason);

        assertEquals(paymentAmount, result.getRefundAmount());
    }

    @Test
    @DisplayName("Should expire pending payments")
    void shouldExpirePendingPayments() {
        Payment payment1 = createPayment(PaymentStatus.PENDING);
        Payment payment2 = createPayment(PaymentStatus.PENDING);
        
        when(paymentRepository.findExpiredPendingPayments(any(LocalDateTime.class)))
            .thenReturn(List.of(payment1, payment2));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        int result = paymentService.expirePayments();

        assertEquals(2, result);
        verify(paymentRepository, times(2)).save(any(Payment.class));
        verify(paymentEventService, times(2)).logEvent(any(), any(), eq("PAYMENT_EXPIRED"));
    }

    @Test
    @DisplayName("Should get payment by ID")
    void shouldGetPaymentById() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = createPayment(PaymentStatus.SUCCESS);
        
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        Payment result = paymentService.getPayment(paymentId);

        assertNotNull(result);
        assertEquals(payment.getId(), result.getId());
    }

    @Test
    @DisplayName("Should throw PaymentNotFoundException when payment not found")
    void shouldThrowPaymentNotFoundExceptionWhenPaymentNotFound() {
        UUID paymentId = UUID.randomUUID();
        
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> {
            paymentService.getPayment(paymentId);
        });
    }

    @Test
    @DisplayName("Should get payments by rider")
    void shouldGetPaymentsByRider() {
        UUID riderId = UUID.randomUUID();
        List<Payment> payments = List.of(
            createPayment(PaymentStatus.SUCCESS),
            createPayment(PaymentStatus.PENDING)
        );
        
        when(paymentRepository.findByRiderIdOrderByCreatedAtDesc(riderId))
            .thenReturn(payments);

        List<Payment> result = paymentService.getPaymentsByRider(riderId);

        assertEquals(2, result.size());
    }

    private Payment createPayment(PaymentStatus status) {
        return Payment.builder()
            .id(UUID.randomUUID())
            .bookingId(UUID.randomUUID())
            .riderId(UUID.randomUUID())
            .amount(new BigDecimal("5000.00"))
            .currency("NGN")
            .status(status)
            .korapayReference("TEST_REF_" + System.currentTimeMillis())
            .idempotencyKey("test-key-" + UUID.randomUUID())
            .initiatedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusMinutes(15))
            .build();
    }
}

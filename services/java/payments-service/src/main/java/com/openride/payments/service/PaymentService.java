package com.openride.payments.service;

import com.openride.payments.config.PaymentConfigProperties;
import com.openride.payments.exception.DuplicatePaymentException;
import com.openride.payments.exception.InvalidStateTransitionException;
import com.openride.payments.exception.PaymentException;
import com.openride.payments.exception.PaymentNotFoundException;
import com.openride.payments.korapay.KorapayClient;
import com.openride.payments.korapay.dto.KorapayChargeRequest;
import com.openride.payments.korapay.dto.KorapayChargeResponse;
import com.openride.payments.korapay.dto.KorapayVerifyResponse;
import com.openride.payments.model.Payment;
import com.openride.payments.model.PaymentMethod;
import com.openride.payments.model.PaymentStatus;
import com.openride.payments.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core service for payment operations.
 * Handles payment lifecycle: initiation, confirmation, refund, and expiration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventService paymentEventService;
    private final IdempotencyService idempotencyService;
    private final KorapayClient korapayClient;
    private final PaymentStateMachine stateMachine;
    private final PaymentConfigProperties paymentConfig;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Value("${korapay.webhook-secret}")
    private String webhookSecret;

    /**
     * Initiates a new payment.
     *
     * @param bookingId booking ID
     * @param riderId rider ID
     * @param amount payment amount
     * @param customerEmail customer email
     * @param customerName customer name
     * @param idempotencyKey unique key to prevent duplicates
     * @return created payment
     * @throws DuplicatePaymentException if payment with same idempotency key exists
     */
    @Transactional
    public Payment initiatePayment(
            UUID bookingId,
            UUID riderId,
            BigDecimal amount,
            String customerEmail,
            String customerName,
            String idempotencyKey
    ) {
        log.info("Initiating payment: bookingId={}, riderId={}, amount={}, idempotencyKey={}", 
            bookingId, riderId, amount, idempotencyKey);

        // Check for duplicate request
        UUID existingPaymentId = idempotencyService.getPaymentIdByIdempotencyKey(idempotencyKey);
        if (existingPaymentId != null) {
            Payment existingPayment = paymentRepository.findById(existingPaymentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + existingPaymentId));
            log.warn("Duplicate payment request: idempotencyKey={}, returning existing payment={}", 
                idempotencyKey, existingPaymentId);
            throw new DuplicatePaymentException("Payment already exists with idempotency key: " + idempotencyKey);
        }

        // Check if booking already has a payment
        if (paymentRepository.existsByBookingId(bookingId)) {
            log.warn("Booking already has a payment: bookingId={}", bookingId);
            throw new DuplicatePaymentException("Booking already has an associated payment: " + bookingId);
        }

        // Generate Korapay reference
        String korapayReference = generateKorapayReference(bookingId);

        // Calculate expiry time
        LocalDateTime expiresAt = LocalDateTime.now()
            .plusMinutes(paymentConfig.getExpiryMinutes());

        // Create payment entity
        Payment payment = Payment.builder()
            .bookingId(bookingId)
            .riderId(riderId)
            .amount(amount)
            .currency(paymentConfig.getCurrency())
            .status(PaymentStatus.INITIATED)
            .korapayReference(korapayReference)
            .korapayCustomerEmail(customerEmail)
            .korapayCustomerName(customerName)
            .idempotencyKey(idempotencyKey)
            .initiatedAt(LocalDateTime.now())
            .expiresAt(expiresAt)
            .build();

        payment = paymentRepository.save(payment);
        log.info("Payment created: id={}, korapayRef={}", payment.getId(), korapayReference);

        // Set idempotency in Redis
        idempotencyService.checkAndSetPaymentIdempotency(idempotencyKey, payment.getId());

        // Log event
        paymentEventService.logEvent(payment, null, "PAYMENT_INITIATED");

        // Initialize payment with Korapay
        try {
            KorapayChargeRequest chargeRequest = buildChargeRequest(payment);
            KorapayChargeResponse chargeResponse = korapayClient.initializeCharge(chargeRequest);

            // Update payment with Korapay response
            payment.setKorapayCheckoutUrl(chargeResponse.getCheckoutUrl());
            stateMachine.transition(payment, PaymentStatus.PENDING);
            payment = paymentRepository.save(payment);

            paymentEventService.logEvent(payment, PaymentStatus.INITIATED, "PAYMENT_PENDING");

            log.info("Payment initialized with Korapay: id={}, checkoutUrl={}", 
                payment.getId(), chargeResponse.getCheckoutUrl());

            return payment;

        } catch (Exception e) {
            log.error("Failed to initialize payment with Korapay: paymentId={}", payment.getId(), e);
            payment.markAsFailed("Korapay initialization failed: " + e.getMessage());
            paymentRepository.save(payment);
            paymentEventService.logEvent(payment, PaymentStatus.INITIATED, "PAYMENT_FAILED");
            throw new PaymentException("Failed to initialize payment", e);
        }
    }

    /**
     * Confirms a payment after successful webhook notification.
     *
     * @param korapayReference Korapay transaction reference
     * @param transactionId Korapay transaction ID
     * @param paymentMethod payment method used
     * @return confirmed payment
     */
    @Transactional
    public Payment confirmPayment(String korapayReference, String transactionId, String paymentMethod) {
        log.info("Confirming payment: korapayRef={}, txnId={}", korapayReference, transactionId);

        Payment payment = paymentRepository.findByKorapayReference(korapayReference)
            .orElseThrow(() -> new PaymentNotFoundException(
                "Payment not found for Korapay reference: " + korapayReference));

        if (payment.getStatus() == PaymentStatus.SUCCESS || 
            payment.getStatus() == PaymentStatus.COMPLETED) {
            log.warn("Payment already confirmed: id={}, status={}", payment.getId(), payment.getStatus());
            return payment;
        }

        PaymentStatus previousStatus = payment.getStatus();

        try {
            // Map payment method
            PaymentMethod method = mapPaymentMethod(paymentMethod);
            
            // Mark as completed
            payment.markAsCompleted(transactionId, method);
            payment = paymentRepository.save(payment);

            paymentEventService.logEvent(payment, previousStatus, "PAYMENT_CONFIRMED");

            log.info("Payment confirmed: id={}, status={}", payment.getId(), payment.getStatus());

            return payment;

        } catch (InvalidStateTransitionException e) {
            log.error("Invalid state transition during confirmation: paymentId={}, currentStatus={}", 
                payment.getId(), payment.getStatus(), e);
            throw e;
        }
    }

    /**
     * Processes a refund for a successful payment.
     *
     * @param paymentId payment ID
     * @param refundAmount amount to refund (null for full refund)
     * @param reason refund reason
     * @return refunded payment
     */
    @Transactional
    public Payment processRefund(UUID paymentId, BigDecimal refundAmount, String reason) {
        log.info("Processing refund: paymentId={}, amount={}, reason={}", paymentId, refundAmount, reason);

        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        if (!payment.canBeRefunded()) {
            throw new PaymentException(
                "Payment cannot be refunded. Status: " + payment.getStatus() + 
                ", Already refunded: " + (payment.getRefundedAt() != null));
        }

        BigDecimal amountToRefund = refundAmount != null ? refundAmount : payment.getAmount();

        if (amountToRefund.compareTo(payment.getAmount()) > 0) {
            throw new PaymentException("Refund amount cannot exceed payment amount");
        }

        PaymentStatus previousStatus = payment.getStatus();

        try {
            // Mark as refunded
            payment.markAsRefunded(amountToRefund, reason);
            payment = paymentRepository.save(payment);

            paymentEventService.logEvent(payment, previousStatus, "PAYMENT_REFUNDED");

            log.info("Payment refunded: id={}, amount={}", payment.getId(), amountToRefund);

            // TODO: Call Korapay refund API when implementing refund feature
            // For now, we just mark it in our system

            return payment;

        } catch (Exception e) {
            log.error("Failed to process refund: paymentId={}", paymentId, e);
            throw new PaymentException("Failed to process refund", e);
        }
    }

    /**
     * Verifies payment status with Korapay.
     *
     * @param paymentId payment ID
     * @return updated payment
     */
    @Transactional
    public Payment verifyPayment(UUID paymentId) {
        log.info("Verifying payment: id={}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));

        try {
            KorapayVerifyResponse verifyResponse = korapayClient.verifyCharge(payment.getKorapayReference());

            if (verifyResponse.isPaymentSuccessful()) {
                return confirmPayment(
                    payment.getKorapayReference(),
                    verifyResponse.getTransactionReference(),
                    verifyResponse.getPaymentMethod()
                );
            } else {
                log.warn("Payment verification failed: id={}, status={}", 
                    paymentId, verifyResponse.getPaymentStatus());
                return payment;
            }

        } catch (Exception e) {
            log.error("Error verifying payment with Korapay: paymentId={}", paymentId, e);
            throw new PaymentException("Failed to verify payment", e);
        }
    }

    /**
     * Marks expired pending payments as failed.
     * Called by scheduled job.
     *
     * @return number of payments expired
     */
    @Transactional
    public int expirePayments() {
        log.info("Running payment expiration job");

        List<Payment> expiredPayments = paymentRepository.findExpiredPendingPayments(LocalDateTime.now());

        for (Payment payment : expiredPayments) {
            PaymentStatus previousStatus = payment.getStatus();
            payment.markAsFailed("Payment expired");
            paymentRepository.save(payment);
            paymentEventService.logEvent(payment, previousStatus, "PAYMENT_EXPIRED");
            log.info("Payment expired: id={}", payment.getId());
        }

        log.info("Expired {} payments", expiredPayments.size());
        return expiredPayments.size();
    }

    /**
     * Retrieves payment by ID.
     *
     * @param paymentId payment ID
     * @return payment
     */
    @Transactional(readOnly = true)
    public Payment getPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
    }

    /**
     * Retrieves payment by booking ID.
     *
     * @param bookingId booking ID
     * @return payment
     */
    @Transactional(readOnly = true)
    public Payment getPaymentByBookingId(UUID bookingId) {
        return paymentRepository.findByBookingId(bookingId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found for booking: " + bookingId));
    }

    /**
     * Retrieves all payments for a rider.
     *
     * @param riderId rider ID
     * @return list of payments
     */
    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByRider(UUID riderId) {
        return paymentRepository.findByRiderIdOrderByCreatedAtDesc(riderId);
    }

    /**
     * Retrieves payments by rider and status.
     *
     * @param riderId rider ID
     * @param status payment status
     * @return list of payments
     */
    @Transactional(readOnly = true)
    public List<Payment> getPaymentsByRiderAndStatus(UUID riderId, PaymentStatus status) {
        return paymentRepository.findByRiderIdAndStatus(riderId, status);
    }

    /**
     * Retrieves all payments (admin only).
     *
     * @return list of all payments
     */
    @Transactional(readOnly = true)
    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    /**
     * Builds Korapay charge request from payment.
     */
    private KorapayChargeRequest buildChargeRequest(Payment payment) {
        return KorapayChargeRequest.builder()
            .reference(payment.getKorapayReference())
            .amount(KorapayChargeRequest.toKobo(payment.getAmount()))
            .currency(payment.getCurrency())
            .customer(KorapayChargeRequest.CustomerInfo.builder()
                .name(payment.getKorapayCustomerName())
                .email(payment.getKorapayCustomerEmail())
                .build())
            .merchantBearsCost(true)
            .metadata(Map.of(
                "booking_id", payment.getBookingId().toString(),
                "rider_id", payment.getRiderId().toString()
            ))
            .channels(new String[]{"card", "bank_transfer", "mobile_money"})
            .build();
    }

    /**
     * Generates unique Korapay reference.
     */
    private String generateKorapayReference(UUID bookingId) {
        return "OPENRIDE_" + bookingId.toString().replace("-", "").substring(0, 16).toUpperCase() 
            + "_" + System.currentTimeMillis();
    }

    /**
     * Maps Korapay payment method string to enum.
     */
    private PaymentMethod mapPaymentMethod(String method) {
        if (method == null) return null;
        
        return switch (method.toLowerCase()) {
            case "card" -> PaymentMethod.CARD;
            case "bank_transfer", "bank" -> PaymentMethod.BANK_TRANSFER;
            case "ussd" -> PaymentMethod.USSD;
            case "mobile_money" -> PaymentMethod.MOBILE_MONEY;
            default -> PaymentMethod.CARD;
        };
    }
}

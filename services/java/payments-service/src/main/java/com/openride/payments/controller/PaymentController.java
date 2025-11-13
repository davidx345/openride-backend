package com.openride.payments.controller;

import com.openride.payments.dto.InitiatePaymentRequest;
import com.openride.payments.dto.PaymentResponse;
import com.openride.payments.model.Payment;
import com.openride.payments.security.UserPrincipal;
import com.openride.payments.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for payment operations.
 * Handles payment initiation, verification, and queries.
 */
@Slf4j
@RestController
@RequestMapping("/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Initiates a new payment.
     *
     * @param request payment initialization request
     * @param principal authenticated user
     * @return payment response with checkout URL
     */
    @PostMapping("/initiate")
    @Operation(summary = "Initiate payment", description = "Creates a new payment and returns Korapay checkout URL")
    public ResponseEntity<PaymentResponse> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.info("Initiating payment: bookingId={}, riderId={}", request.getBookingId(), principal.getUserId());

        Payment payment = paymentService.initiatePayment(
            request.getBookingId(),
            principal.getUserId(),
            request.getAmount(),
            request.getCustomerEmail(),
            request.getCustomerName(),
            request.getIdempotencyKey()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(PaymentResponse.fromEntity(payment));
    }

    /**
     * Retrieves payment by ID.
     *
     * @param paymentId payment ID
     * @param principal authenticated user
     * @return payment details
     */
    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment", description = "Retrieves payment details by ID")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.info("Getting payment: id={}, riderId={}", paymentId, principal.getUserId());

        Payment payment = paymentService.getPayment(paymentId);

        // Ensure user can only access their own payments
        if (!payment.getRiderId().equals(principal.getUserId()) && !principal.isAdmin()) {
            log.warn("Unauthorized access attempt: paymentId={}, userId={}", paymentId, principal.getUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(PaymentResponse.fromEntity(payment));
    }

    /**
     * Retrieves payment by booking ID.
     *
     * @param bookingId booking ID
     * @param principal authenticated user
     * @return payment details
     */
    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get payment by booking", description = "Retrieves payment associated with a booking")
    public ResponseEntity<PaymentResponse> getPaymentByBooking(
            @PathVariable UUID bookingId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.info("Getting payment by booking: bookingId={}, riderId={}", bookingId, principal.getUserId());

        Payment payment = paymentService.getPaymentByBookingId(bookingId);

        // Ensure user can only access their own payments
        if (!payment.getRiderId().equals(principal.getUserId()) && !principal.isAdmin()) {
            log.warn("Unauthorized access attempt: bookingId={}, userId={}", bookingId, principal.getUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(PaymentResponse.fromEntity(payment));
    }

    /**
     * Retrieves all payments for the authenticated rider.
     *
     * @param principal authenticated user
     * @return list of payments
     */
    @GetMapping("/my-payments")
    @Operation(summary = "Get my payments", description = "Retrieves all payments for the authenticated rider")
    public ResponseEntity<List<PaymentResponse>> getMyPayments(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.info("Getting payments for rider: riderId={}", principal.getUserId());

        List<Payment> payments = paymentService.getPaymentsByRider(principal.getUserId());

        List<PaymentResponse> response = payments.stream()
            .map(PaymentResponse::fromEntity)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Manually verifies payment status with Korapay.
     * Useful for checking payment status if webhook was missed.
     *
     * @param paymentId payment ID
     * @param principal authenticated user
     * @return updated payment details
     */
    @PostMapping("/{paymentId}/verify")
    @Operation(summary = "Verify payment", description = "Manually verifies payment status with Korapay")
    public ResponseEntity<PaymentResponse> verifyPayment(
            @PathVariable UUID paymentId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        log.info("Verifying payment: id={}, riderId={}", paymentId, principal.getUserId());

        Payment payment = paymentService.getPayment(paymentId);

        // Ensure user can only verify their own payments
        if (!payment.getRiderId().equals(principal.getUserId()) && !principal.isAdmin()) {
            log.warn("Unauthorized verification attempt: paymentId={}, userId={}", paymentId, principal.getUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Payment verifiedPayment = paymentService.verifyPayment(paymentId);

        return ResponseEntity.ok(PaymentResponse.fromEntity(verifiedPayment));
    }
}

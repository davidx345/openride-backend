package com.openride.payments.controller;

import com.openride.payments.dto.PaymentResponse;
import com.openride.payments.dto.ReconciliationResponse;
import com.openride.payments.dto.RefundRequest;
import com.openride.payments.model.Payment;
import com.openride.payments.model.PaymentStatus;
import com.openride.payments.model.ReconciliationRecord;
import com.openride.payments.service.PaymentService;
import com.openride.payments.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Admin controller for payment management.
 * Requires ADMIN role.
 */
@Slf4j
@RestController
@RequestMapping("/v1/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Payments", description = "Admin endpoints for payment management")
@SecurityRequirement(name = "bearerAuth")
public class AdminPaymentController {

    private final PaymentService paymentService;
    private final ReconciliationService reconciliationService;

    /**
     * Retrieves all payments with optional filters.
     *
     * @param status optional status filter
     * @param riderId optional rider ID filter
     * @return list of payments
     */
    @GetMapping
    @Operation(summary = "List all payments", description = "Retrieves all payments with optional filters (Admin only)")
    public ResponseEntity<List<PaymentResponse>> getAllPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) UUID riderId
    ) {
        log.info("Admin: Getting payments: status={}, riderId={}", status, riderId);

        List<Payment> payments;

        if (riderId != null && status != null) {
            payments = paymentService.getPaymentsByRiderAndStatus(riderId, status);
        } else if (riderId != null) {
            payments = paymentService.getPaymentsByRider(riderId);
        } else if (status != null) {
            payments = paymentService.getAllPayments().stream()
                .filter(p -> p.getStatus() == status)
                .toList();
        } else {
            payments = paymentService.getAllPayments();
        }

        List<PaymentResponse> response = payments.stream()
            .map(PaymentResponse::fromEntity)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Processes a refund for a payment.
     *
     * @param paymentId payment ID
     * @param request refund request
     * @return refunded payment details
     */
    @PostMapping("/{paymentId}/refund")
    @Operation(summary = "Refund payment", description = "Processes a refund for a successful payment (Admin only)")
    public ResponseEntity<PaymentResponse> refundPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody RefundRequest request
    ) {
        log.info("Admin: Processing refund: paymentId={}, amount={}, reason={}", 
            paymentId, request.getAmount(), request.getReason());

        Payment refundedPayment = paymentService.processRefund(
            paymentId,
            request.getAmount(),
            request.getReason()
        );

        return ResponseEntity.ok(PaymentResponse.fromEntity(refundedPayment));
    }

    /**
     * Manually runs payment expiration job.
     *
     * @return number of payments expired
     */
    @PostMapping("/expire")
    @Operation(summary = "Expire payments", description = "Manually runs payment expiration job (Admin only)")
    public ResponseEntity<Map<String, Object>> expirePayments() {
        log.info("Admin: Manually running payment expiration");

        int expired = paymentService.expirePayments();

        return ResponseEntity.ok(Map.of(
            "message", "Payment expiration completed",
            "expiredCount", expired
        ));
    }

    /**
     * Runs reconciliation for a specific date.
     *
     * @param date date to reconcile (defaults to yesterday)
     * @return reconciliation result
     */
    @PostMapping("/reconciliation/run")
    @Operation(summary = "Run reconciliation", description = "Manually runs payment reconciliation for a date (Admin only)")
    public ResponseEntity<ReconciliationResponse> runReconciliation(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now().minusDays(1);
        
        log.info("Admin: Running reconciliation for {}", targetDate);

        ReconciliationRecord record = reconciliationService.reconcilePayments(targetDate);

        return ResponseEntity.ok(ReconciliationResponse.fromEntity(record));
    }

    /**
     * Gets latest reconciliation records.
     *
     * @param limit number of records to retrieve (default 10)
     * @return list of reconciliation records
     */
    @GetMapping("/reconciliation")
    @Operation(summary = "Get reconciliation records", description = "Retrieves latest reconciliation records (Admin only)")
    public ResponseEntity<List<ReconciliationResponse>> getReconciliations(
            @RequestParam(defaultValue = "10") int limit
    ) {
        log.info("Admin: Getting latest {} reconciliation records", limit);

        List<ReconciliationRecord> records = reconciliationService.getLatestReconciliations(limit);

        List<ReconciliationResponse> response = records.stream()
            .map(ReconciliationResponse::fromEntity)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Gets reconciliations with discrepancies.
     *
     * @return list of records with discrepancies
     */
    @GetMapping("/reconciliation/discrepancies")
    @Operation(summary = "Get discrepancies", description = "Retrieves reconciliation records with discrepancies (Admin only)")
    public ResponseEntity<List<ReconciliationResponse>> getDiscrepancies() {
        log.info("Admin: Getting reconciliation discrepancies");

        List<ReconciliationRecord> records = reconciliationService.getDiscrepancies();

        List<ReconciliationResponse> response = records.stream()
            .map(ReconciliationResponse::fromEntity)
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }
}

package com.openride.payouts.controller;

import com.openride.payouts.dto.PayoutResponse;
import com.openride.payouts.dto.PayoutReviewRequest;
import com.openride.payouts.dto.SettlementResponse;
import com.openride.payouts.model.enums.SettlementStatus;
import com.openride.payouts.service.PayoutService;
import com.openride.payouts.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for admin payout and settlement operations.
 */
@Tag(name = "Admin - Payouts & Settlements", description = "Admin endpoints for payout review and settlement processing")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v1/admin/payouts")
@RequiredArgsConstructor
public class AdminPayoutController {

    private final PayoutService payoutService;
    private final SettlementService settlementService;

    // ==================== Payout Review ====================

    @Operation(summary = "Get pending payouts", description = "Get all pending payout requests for admin review")
    @GetMapping("/pending")
    public ResponseEntity<Page<PayoutResponse>> getPendingPayouts(
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        Page<PayoutResponse> payouts = payoutService.getPendingPayouts(pageable);
        return ResponseEntity.ok(payouts);
    }

    @Operation(summary = "Approve payout", description = "Approve a payout request")
    @PostMapping("/{payoutId}/approve")
    public ResponseEntity<PayoutResponse> approvePayout(
            @Parameter(description = "Admin ID", required = true)
            @RequestHeader("X-Admin-Id") UUID adminId,
            @Parameter(description = "Payout request ID", required = true)
            @PathVariable UUID payoutId,
            @Valid @RequestBody PayoutReviewRequest request
    ) {
        PayoutResponse response = payoutService.approvePayoutRequest(payoutId, adminId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Reject payout", description = "Reject a payout request")
    @PostMapping("/{payoutId}/reject")
    public ResponseEntity<PayoutResponse> rejectPayout(
            @Parameter(description = "Admin ID", required = true)
            @RequestHeader("X-Admin-Id") UUID adminId,
            @Parameter(description = "Payout request ID", required = true)
            @PathVariable UUID payoutId,
            @Valid @RequestBody PayoutReviewRequest request
    ) {
        PayoutResponse response = payoutService.rejectPayoutRequest(payoutId, adminId, request);
        return ResponseEntity.ok(response);
    }

    // ==================== Settlement Management ====================

    @Operation(summary = "Create settlement batch", description = "Create a new settlement batch from approved payouts")
    @PostMapping("/settlements")
    public ResponseEntity<SettlementResponse> createSettlement(
            @Parameter(description = "Admin ID", required = true)
            @RequestHeader("X-Admin-Id") UUID adminId
    ) {
        SettlementResponse response = settlementService.createSettlementBatch(adminId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Process settlement", description = "Process a settlement batch and initiate bank transfers")
    @PostMapping("/settlements/{settlementId}/process")
    public ResponseEntity<SettlementResponse> processSettlement(
            @Parameter(description = "Settlement ID", required = true)
            @PathVariable UUID settlementId
    ) {
        SettlementResponse response = settlementService.processSettlement(settlementId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get settlements", description = "Get settlements with optional status filter")
    @GetMapping("/settlements")
    public ResponseEntity<Page<SettlementResponse>> getSettlements(
            @Parameter(description = "Filter by status (optional)")
            @RequestParam(required = false) SettlementStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SettlementResponse> settlements = settlementService.getSettlements(status, pageable);
        return ResponseEntity.ok(settlements);
    }

    @Operation(summary = "Get settlement by ID", description = "Get a specific settlement by ID")
    @GetMapping("/settlements/{settlementId}")
    public ResponseEntity<SettlementResponse> getSettlement(
            @Parameter(description = "Settlement ID", required = true)
            @PathVariable UUID settlementId
    ) {
        SettlementResponse settlement = settlementService.getSettlement(settlementId);
        return ResponseEntity.ok(settlement);
    }

    @Operation(summary = "Retry failed settlement", description = "Retry a failed settlement batch")
    @PostMapping("/settlements/{settlementId}/retry")
    public ResponseEntity<SettlementResponse> retrySettlement(
            @Parameter(description = "Settlement ID", required = true)
            @PathVariable UUID settlementId
    ) {
        SettlementResponse response = settlementService.retrySettlement(settlementId);
        return ResponseEntity.ok(response);
    }
}

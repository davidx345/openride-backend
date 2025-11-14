package com.openride.payouts.controller;

import com.openride.payouts.dto.PayoutRequestDto;
import com.openride.payouts.dto.PayoutResponse;
import com.openride.payouts.model.enums.PayoutStatus;
import com.openride.payouts.service.PayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * Controller for driver payout requests.
 */
@Tag(name = "Payouts", description = "Driver payout request management")
@RestController
@RequestMapping("/v1/payouts")
@RequiredArgsConstructor
public class PayoutController {

    private final PayoutService payoutService;

    @Operation(summary = "Request payout", description = "Submit a new payout request")
    @PostMapping("/request")
    public ResponseEntity<PayoutResponse> requestPayout(
            @Parameter(description = "Driver ID", required = true)
            @RequestHeader("X-Driver-Id") UUID driverId,
            @Valid @RequestBody PayoutRequestDto request
    ) {
        PayoutResponse response = payoutService.requestPayout(driverId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get payout requests", description = "Get payout requests for driver with optional status filter")
    @GetMapping("/requests")
    public ResponseEntity<Page<PayoutResponse>> getPayoutRequests(
            @Parameter(description = "Driver ID", required = true)
            @RequestHeader("X-Driver-Id") UUID driverId,
            @Parameter(description = "Filter by status (optional)")
            @RequestParam(required = false) PayoutStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<PayoutResponse> payouts = payoutService.getPayoutRequests(driverId, status, pageable);
        return ResponseEntity.ok(payouts);
    }

    @Operation(summary = "Get payout by ID", description = "Get a specific payout request by ID")
    @GetMapping("/requests/{payoutId}")
    public ResponseEntity<PayoutResponse> getPayoutById(
            @Parameter(description = "Driver ID", required = true)
            @RequestHeader("X-Driver-Id") UUID driverId,
            @Parameter(description = "Payout request ID", required = true)
            @PathVariable UUID payoutId
    ) {
        // Get payout and verify ownership
        Page<PayoutResponse> payouts = payoutService.getPayoutRequests(driverId, null, Pageable.unpaged());
        PayoutResponse payout = payouts.stream()
                .filter(p -> p.getId().equals(payoutId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Payout not found"));
        
        return ResponseEntity.ok(payout);
    }
}

package com.openride.admin.controller;

import com.openride.admin.dto.CreateDisputeRequest;
import com.openride.admin.dto.DisputeResponse;
import com.openride.admin.dto.ResolveDisputeRequest;
import com.openride.admin.service.DisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller for dispute management.
 */
@Slf4j
@RestController
@RequestMapping("/v1/admin/disputes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Disputes", description = "Dispute resolution and management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class DisputeController {

    private final DisputeService disputeService;

    /**
     * Create a new dispute.
     */
    @PostMapping
    @Operation(summary = "Create dispute", description = "Create a new dispute/support ticket")
    public ResponseEntity<DisputeResponse> createDispute(
            @Valid @RequestBody CreateDisputeRequest request
    ) {
        log.info("Creating dispute for booking: {}", request.getBookingId());

        DisputeResponse response = disputeService.createDispute(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all open disputes.
     */
    @GetMapping
    @Operation(summary = "Get disputes", description = "Get disputes with optional status filter")
    public ResponseEntity<Page<DisputeResponse>> getDisputes(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Fetching disputes: status={}, page={}", status, page);

        Page<DisputeResponse> disputes;

        if (status != null && !status.isBlank()) {
            disputes = disputeService.getDisputesByStatus(status, page, size);
        } else {
            disputes = disputeService.getOpenDisputes(page, size);
        }

        return ResponseEntity.ok(disputes);
    }

    /**
     * Get dispute by ID.
     */
    @GetMapping("/{disputeId}")
    @Operation(summary = "Get dispute details", description = "Get full details of a specific dispute")
    public ResponseEntity<DisputeResponse> getDispute(@PathVariable UUID disputeId) {
        log.info("Fetching dispute: {}", disputeId);

        DisputeResponse response = disputeService.getDisputeById(disputeId);

        return ResponseEntity.ok(response);
    }

    /**
     * Resolve a dispute.
     */
    @PatchMapping("/{disputeId}/resolve")
    @Operation(summary = "Resolve dispute", description = "Resolve or reject a dispute")
    public ResponseEntity<DisputeResponse> resolveDispute(
            @PathVariable UUID disputeId,
            @Valid @RequestBody ResolveDisputeRequest request,
            Authentication authentication
    ) {
        log.info("Resolving dispute: {}", disputeId);

        UUID adminId = UUID.fromString(authentication.getName());
        DisputeResponse response = disputeService.resolveDispute(disputeId, request, adminId);

        return ResponseEntity.ok(response);
    }

    /**
     * Assign dispute to admin for investigation.
     */
    @PatchMapping("/{disputeId}/assign")
    @Operation(summary = "Assign dispute", description = "Assign dispute to admin (move to IN_PROGRESS)")
    public ResponseEntity<DisputeResponse> assignDispute(
            @PathVariable UUID disputeId,
            Authentication authentication
    ) {
        log.info("Assigning dispute: {}", disputeId);

        UUID adminId = UUID.fromString(authentication.getName());
        DisputeResponse response = disputeService.assignDispute(disputeId, adminId);

        return ResponseEntity.ok(response);
    }

    /**
     * Get disputes for a specific booking.
     */
    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get disputes by booking", description = "Get all disputes for a specific booking")
    public ResponseEntity<List<DisputeResponse>> getDisputesByBooking(@PathVariable UUID bookingId) {
        log.info("Fetching disputes for booking: {}", bookingId);

        List<DisputeResponse> disputes = disputeService.getDisputesByBooking(bookingId);

        return ResponseEntity.ok(disputes);
    }

    /**
     * Get dispute statistics.
     */
    @GetMapping("/statistics")
    @Operation(summary = "Get dispute statistics", description = "Get aggregated dispute statistics")
    public ResponseEntity<Map<String, Long>> getStatistics() {
        log.info("Fetching dispute statistics");

        Map<String, Long> statistics = disputeService.getDisputeStatistics();

        return ResponseEntity.ok(statistics);
    }
}

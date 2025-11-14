package com.openride.admin.controller;

import com.openride.admin.dto.CreateSuspensionRequest;
import com.openride.admin.dto.UserSuspensionResponse;
import com.openride.admin.service.SuspensionService;
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
import java.util.Optional;
import java.util.UUID;

/**
 * Controller for user suspension/ban management.
 */
@Slf4j
@RestController
@RequestMapping("/v1/admin/suspensions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Suspensions", description = "User suspension and ban management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class SuspensionController {

    private final SuspensionService suspensionService;

    /**
     * Suspend a user.
     */
    @PostMapping
    @Operation(summary = "Suspend user", description = "Create a new user suspension (temporary or permanent)")
    public ResponseEntity<UserSuspensionResponse> suspendUser(
            @Valid @RequestBody CreateSuspensionRequest request,
            Authentication authentication
    ) {
        log.info("Admin suspending user: {}", request.getUserId());

        UUID adminId = UUID.fromString(authentication.getName());
        UserSuspensionResponse response = suspensionService.suspendUser(request, adminId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Lift a suspension.
     */
    @DeleteMapping("/{suspensionId}")
    @Operation(summary = "Lift suspension", description = "Deactivate a user suspension")
    public ResponseEntity<UserSuspensionResponse> liftSuspension(
            @PathVariable UUID suspensionId,
            Authentication authentication
    ) {
        log.info("Admin lifting suspension: {}", suspensionId);

        UUID adminId = UUID.fromString(authentication.getName());
        UserSuspensionResponse response = suspensionService.liftSuspension(suspensionId, adminId);

        return ResponseEntity.ok(response);
    }

    /**
     * Check if user is suspended.
     */
    @GetMapping("/check/{userId}")
    @Operation(summary = "Check suspension status", description = "Check if a user is currently suspended")
    public ResponseEntity<Map<String, Object>> checkSuspensionStatus(@PathVariable UUID userId) {
        log.info("Checking suspension status for user: {}", userId);

        boolean isSuspended = suspensionService.isUserSuspended(userId);
        Optional<UserSuspensionResponse> activeSuspension = suspensionService.getActiveSuspension(userId);

        Map<String, Object> response = Map.of(
                "isSuspended", isSuspended,
                "activeSuspension", activeSuspension.orElse(null)
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Get all suspensions for a user.
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user suspensions", description = "Get all suspensions for a specific user")
    public ResponseEntity<List<UserSuspensionResponse>> getUserSuspensions(@PathVariable UUID userId) {
        log.info("Fetching suspensions for user: {}", userId);

        List<UserSuspensionResponse> suspensions = suspensionService.getUserSuspensions(userId);

        return ResponseEntity.ok(suspensions);
    }

    /**
     * Get all active suspensions.
     */
    @GetMapping("/active")
    @Operation(summary = "Get active suspensions", description = "Get all currently active suspensions")
    public ResponseEntity<Page<UserSuspensionResponse>> getActiveSuspensions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Fetching active suspensions: page={}", page);

        Page<UserSuspensionResponse> suspensions = suspensionService.getActiveSuspensions(page, size);

        return ResponseEntity.ok(suspensions);
    }
}

package com.openride.user.controller;

import com.openride.commons.response.ApiResponse;
import com.openride.user.dto.UpdateKycStatusRequest;
import com.openride.user.dto.UserResponse;
import com.openride.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for admin operations.
 */
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin", description = "Admin-only endpoints for user management")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;

    /**
     * Gets all drivers with pending KYC status.
     *
     * @return API response with list of users
     */
    @GetMapping("/drivers/pending-verification")
    @Operation(summary = "Get Pending KYC Drivers", description = "Gets all drivers awaiting KYC verification")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getPendingKycDrivers() {
        log.info("Getting pending KYC drivers");

        List<UserResponse> response = userService.getPendingKycDrivers();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates KYC status for a user.
     *
     * @param userId user ID
     * @param request update KYC status request
     * @return API response with updated user data
     */
    @PatchMapping("/users/{userId}/kyc-status")
    @Operation(summary = "Update KYC Status", description = "Updates KYC status (VERIFIED/REJECTED)")
    public ResponseEntity<ApiResponse<UserResponse>> updateKycStatus(
        @PathVariable UUID userId,
        @Valid @RequestBody UpdateKycStatusRequest request
    ) {
        log.info("Admin updating KYC status for user: {} to {}", userId, request.getStatus());

        UserResponse response = userService.updateKycStatus(userId, request);

        return ResponseEntity.ok(
            ApiResponse.success(response, "KYC status updated successfully")
        );
    }
}

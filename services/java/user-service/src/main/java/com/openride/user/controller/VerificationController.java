package com.openride.user.controller;

import com.openride.commons.response.ApiResponse;
import com.openride.user.dto.AffiliationVerificationRequest;
import com.openride.user.dto.CaptainVerificationRequest;
import com.openride.user.dto.IdentityVerificationRequest;
import com.openride.user.dto.VehicleRequest;
import com.openride.user.dto.VehicleResponse;
import com.openride.user.dto.VerificationStatusResponse;
import com.openride.user.service.VerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for user verification operations.
 * Handles identity, affiliation, and captain verification workflows.
 */
@RestController
@RequestMapping("/v1/verification")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Verification", description = "User verification and KYC endpoints")
public class VerificationController {

    private final VerificationService verificationService;

    /**
     * Gets current verification status.
     *
     * @param authentication authentication object
     * @return verification status response
     */
    @GetMapping("/status")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get Verification Status", 
        description = "Gets current verification status and next steps")
    public ResponseEntity<ApiResponse<VerificationStatusResponse>> getStatus(
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        log.info("Getting verification status for user: {}", userId);

        VerificationStatusResponse response = verificationService.getVerificationStatus(userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Submits identity verification (NIN + Government ID + Selfie).
     *
     * @param authentication authentication object
     * @param request identity verification request
     * @return updated verification status
     */
    @PostMapping("/identity")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Submit Identity Verification", 
        description = "Submits NIN, government ID, and selfie for identity verification")
    public ResponseEntity<ApiResponse<VerificationStatusResponse>> submitIdentity(
            Authentication authentication,
            @Valid @RequestBody IdentityVerificationRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        log.info("Submitting identity verification for user: {}", userId);

        VerificationStatusResponse response = verificationService
            .submitIdentityVerification(userId, request);

        return ResponseEntity.ok(
            ApiResponse.success(response, "Identity verification submitted successfully")
        );
    }

    /**
     * Submits affiliation verification (Work/Student).
     *
     * @param authentication authentication object
     * @param request affiliation verification request
     * @return updated verification status
     */
    @PostMapping("/affiliation")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Submit Affiliation Verification", 
        description = "Submits work/student verification via ID card or email")
    public ResponseEntity<ApiResponse<VerificationStatusResponse>> submitAffiliation(
            Authentication authentication,
            @Valid @RequestBody AffiliationVerificationRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        log.info("Submitting affiliation verification for user: {}", userId);

        VerificationStatusResponse response = verificationService
            .submitAffiliationVerification(userId, request);

        String message = request.getVerificationMethod().name().equals("EMAIL")
            ? "Verification email sent. Please check your inbox."
            : "Affiliation verification submitted successfully";

        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    /**
     * Verifies email for affiliation verification.
     *
     * @param token email verification token
     * @return updated verification status
     */
    @GetMapping("/affiliation/verify-email")
    @Operation(summary = "Verify Affiliation Email", 
        description = "Verifies email address for affiliation verification")
    public ResponseEntity<ApiResponse<VerificationStatusResponse>> verifyEmail(
            @RequestParam String token) {
        log.info("Verifying affiliation email");

        VerificationStatusResponse response = verificationService
            .verifyAffiliationEmail(token);

        return ResponseEntity.ok(
            ApiResponse.success(response, "Email verified successfully")
        );
    }

    /**
     * Submits Captain (driver) verification.
     *
     * @param authentication authentication object
     * @param request captain verification request
     * @return updated verification status
     */
    @PostMapping("/captain")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Submit Captain Verification", 
        description = "Submits driver's license for Captain verification")
    public ResponseEntity<ApiResponse<VerificationStatusResponse>> submitCaptain(
            Authentication authentication,
            @Valid @RequestBody CaptainVerificationRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        log.info("Submitting captain verification for user: {}", userId);

        VerificationStatusResponse response = verificationService
            .submitCaptainVerification(userId, request);

        return ResponseEntity.ok(
            ApiResponse.success(response, "Captain verification submitted successfully")
        );
    }

    /**
     * Registers a vehicle for Captain.
     *
     * @param authentication authentication object
     * @param request vehicle request
     * @return vehicle response
     */
    @PostMapping("/vehicles")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Register Vehicle", 
        description = "Registers a vehicle for Captain")
    public ResponseEntity<ApiResponse<VehicleResponse>> registerVehicle(
            Authentication authentication,
            @Valid @RequestBody VehicleRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        log.info("Registering vehicle for user: {}", userId);

        VehicleResponse response = verificationService.registerVehicle(userId, request);

        return ResponseEntity.ok(
            ApiResponse.success(response, "Vehicle registered successfully")
        );
    }

    /**
     * Gets all vehicles for current user.
     *
     * @param authentication authentication object
     * @return list of vehicles
     */
    @GetMapping("/vehicles")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get Vehicles", description = "Gets all registered vehicles")
    public ResponseEntity<ApiResponse<List<VehicleResponse>>> getVehicles(
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        log.info("Getting vehicles for user: {}", userId);

        List<VehicleResponse> response = verificationService.getVehicles(userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Sets a vehicle as active.
     *
     * @param authentication authentication object
     * @param vehicleId vehicle ID
     * @return updated vehicle response
     */
    @PutMapping("/vehicles/{vehicleId}/activate")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Activate Vehicle", description = "Sets a vehicle as the active vehicle")
    public ResponseEntity<ApiResponse<VehicleResponse>> activateVehicle(
            Authentication authentication,
            @PathVariable UUID vehicleId) {
        UUID userId = UUID.fromString(authentication.getName());
        log.info("Activating vehicle {} for user: {}", vehicleId, userId);

        VehicleResponse response = verificationService.setActiveVehicle(userId, vehicleId);

        return ResponseEntity.ok(
            ApiResponse.success(response, "Vehicle activated successfully")
        );
    }
}

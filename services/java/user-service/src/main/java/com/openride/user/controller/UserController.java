package com.openride.user.controller;

import com.openride.commons.response.ApiResponse;
import com.openride.user.dto.CreateUserRequest;
import com.openride.user.dto.KycDocumentsRequest;
import com.openride.user.dto.UpdateUserRequest;
import com.openride.user.dto.UserResponse;
import com.openride.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for user management operations.
 */
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users", description = "User management endpoints")
public class UserController {

    private final UserService userService;

    /**
     * Creates a new user or returns existing user.
     * Internal endpoint called by Auth Service.
     *
     * @param request create user request
     * @return API response with user data
     */
    @PostMapping("/users")
    @Operation(summary = "Create User", description = "Creates new user or returns existing (internal)")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
        @Valid @RequestBody CreateUserRequest request
    ) {
        log.info("Received create user request for phone: {}", request.getPhone());

        UserResponse response = userService.createOrGetUser(request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Gets current user profile.
     *
     * @param authentication authentication object
     * @return API response with user data
     */
    @GetMapping("/users/me")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get Current User", description = "Gets current user profile")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(Authentication authentication) {
        String userId = authentication.getName();
        log.info("Getting current user: {}", userId);

        UserResponse response = userService.getUserById(UUID.fromString(userId));

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Gets user by ID.
     *
     * @param userId user ID
     * @return API response with user data
     */
    @GetMapping("/users/{userId}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get User by ID", description = "Gets user by ID (internal)")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID userId) {
        log.info("Getting user by ID: {}", userId);

        UserResponse response = userService.getUserById(userId);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates current user profile.
     *
     * @param authentication authentication object
     * @param request update request
     * @return API response with updated user data
     */
    @PatchMapping("/users/me")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Update Profile", description = "Updates current user profile")
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUser(
        Authentication authentication,
        @Valid @RequestBody UpdateUserRequest request
    ) {
        String userId = authentication.getName();
        log.info("Updating user: {}", userId);

        UserResponse response = userService.updateUser(UUID.fromString(userId), request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Upgrades current user to driver role.
     *
     * @param authentication authentication object
     * @return API response with updated user data
     */
    @PostMapping("/users/upgrade-to-driver")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Upgrade to Driver", description = "Upgrades rider to driver role")
    public ResponseEntity<ApiResponse<UserResponse>> upgradeToDriver(Authentication authentication) {
        String userId = authentication.getName();
        log.info("Upgrading user to driver: {}", userId);

        UserResponse response = userService.upgradeToDriver(UUID.fromString(userId));

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Submits KYC documents for driver verification.
     *
     * @param authentication authentication object
     * @param request KYC documents request
     * @return API response with updated user data
     */
    @PostMapping("/drivers/kyc-documents")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Submit KYC Documents", description = "Submits KYC documents for verification")
    public ResponseEntity<ApiResponse<UserResponse>> submitKycDocuments(
        Authentication authentication,
        @Valid @RequestBody KycDocumentsRequest request
    ) {
        String userId = authentication.getName();
        log.info("Submitting KYC documents for user: {}", userId);

        UserResponse response = userService.submitKycDocuments(UUID.fromString(userId), request);

        return ResponseEntity.ok(
            ApiResponse.success(response, "KYC documents submitted for review")
        );
    }
}

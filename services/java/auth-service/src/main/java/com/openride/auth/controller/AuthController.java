package com.openride.auth.controller;

import com.openride.auth.dto.AuthResponse;
import com.openride.auth.dto.RefreshTokenRequest;
import com.openride.auth.dto.RefreshTokenResponse;
import com.openride.auth.dto.SendOtpRequest;
import com.openride.auth.dto.SendOtpResponse;
import com.openride.auth.dto.VerifyOtpRequest;
import com.openride.auth.service.AuthService;
import com.openride.commons.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication operations.
 */
@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "OTP and JWT authentication endpoints")
public class AuthController {

    private final AuthService authService;

    /**
     * Sends OTP to the specified phone number.
     *
     * @param request send OTP request
     * @return API response with OTP send result
     */
    @PostMapping("/send-otp")
    @Operation(summary = "Send OTP", description = "Sends an OTP code to the specified phone number")
    public ResponseEntity<ApiResponse<SendOtpResponse>> sendOtp(
        @Valid @RequestBody SendOtpRequest request
    ) {
        log.info("Received send-otp request for phone: {}", request.getPhone());
        
        SendOtpResponse response = authService.sendOtp(request.getPhone());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Verifies OTP and returns authentication tokens.
     *
     * @param request verify OTP request
     * @return API response with JWT tokens and user info
     */
    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP", description = "Verifies OTP code and returns JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(
        @Valid @RequestBody VerifyOtpRequest request
    ) {
        log.info("Received verify-otp request for phone: {}", request.getPhone());
        
        AuthResponse response = authService.verifyOtp(request.getPhone(), request.getCode());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Refreshes access token using refresh token.
     *
     * @param request refresh token request
     * @return API response with new access token
     */
    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh Token", description = "Generates new access token using refresh token")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(
        @Valid @RequestBody RefreshTokenRequest request
    ) {
        log.info("Received refresh-token request");
        
        RefreshTokenResponse response = authService.refreshAccessToken(request.getRefreshToken());
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Logs out user by invalidating refresh token.
     *
     * @param request refresh token request
     * @return API response confirming logout
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Invalidates the refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(
        @Valid @RequestBody RefreshTokenRequest request
    ) {
        log.info("Received logout request");
        
        authService.logout(request.getRefreshToken());
        
        return ResponseEntity.ok(
            ApiResponse.success(null, "Logged out successfully")
        );
    }
}

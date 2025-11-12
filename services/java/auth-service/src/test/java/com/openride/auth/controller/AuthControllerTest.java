package com.openride.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openride.auth.dto.*;
import com.openride.auth.service.AuthService;
import com.openride.commons.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AuthController.
 * Tests all REST endpoints with MockMvc.
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    private static final String BASE_URL = "/api/v1/auth";
    private static final String TEST_PHONE = "+2348012345678";
    private static final String TEST_OTP = "123456";

    @Test
    void sendOtp_ValidRequest_ReturnsSuccess() throws Exception {
        // Given
        SendOtpRequest request = new SendOtpRequest(TEST_PHONE);
        SendOtpResponse response = new SendOtpResponse("OTP sent successfully", 300);
        
        when(authService.sendOtp(any(SendOtpRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("OTP sent successfully"))
            .andExpect(jsonPath("$.expiresIn").value(300));

        verify(authService).sendOtp(any(SendOtpRequest.class));
    }

    @Test
    void sendOtp_InvalidPhoneFormat_ReturnsBadRequest() throws Exception {
        // Given
        SendOtpRequest request = new SendOtpRequest("invalid-phone");

        // When & Then
        mockMvc.perform(post(BASE_URL + "/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.phone").exists());

        verify(authService, never()).sendOtp(any());
    }

    @Test
    void sendOtp_NullPhone_ReturnsBadRequest() throws Exception {
        // Given
        SendOtpRequest request = new SendOtpRequest(null);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.phone").exists());

        verify(authService, never()).sendOtp(any());
    }

    @Test
    void sendOtp_RateLimitExceeded_ReturnsTooManyRequests() throws Exception {
        // Given
        SendOtpRequest request = new SendOtpRequest(TEST_PHONE);
        
        when(authService.sendOtp(any(SendOtpRequest.class)))
            .thenThrow(new BusinessException("RATE_LIMIT_EXCEEDED", "Too many OTP requests", HttpStatus.TOO_MANY_REQUESTS));

        // When & Then
        mockMvc.perform(post(BASE_URL + "/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.errorCode").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.message").value("Too many OTP requests"));
    }

    @Test
    void verifyOtp_ValidRequest_ReturnsAuthResponse() throws Exception {
        // Given
        VerifyOtpRequest request = new VerifyOtpRequest(TEST_PHONE, TEST_OTP);
        UserInfo userInfo = new UserInfo(
            "user-123",
            TEST_PHONE,
            "John Doe",
            null,
            "RIDER",
            "NONE",
            null,
            true
        );
        AuthResponse response = new AuthResponse(
            "access-token",
            "refresh-token",
            userInfo
        );
        
        when(authService.verifyOtp(any(VerifyOtpRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
            .andExpect(jsonPath("$.user.userId").value("user-123"))
            .andExpect(jsonPath("$.user.phone").value(TEST_PHONE))
            .andExpect(jsonPath("$.user.role").value("RIDER"));

        verify(authService).verifyOtp(any(VerifyOtpRequest.class));
    }

    @Test
    void verifyOtp_InvalidOtp_ReturnsBadRequest() throws Exception {
        // Given
        VerifyOtpRequest request = new VerifyOtpRequest(TEST_PHONE, "wrong");
        
        when(authService.verifyOtp(any(VerifyOtpRequest.class)))
            .thenThrow(new BusinessException("OTP_INVALID", "Invalid OTP code", HttpStatus.BAD_REQUEST));

        // When & Then
        mockMvc.perform(post(BASE_URL + "/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("OTP_INVALID"));
    }

    @Test
    void verifyOtp_ExpiredOtp_ReturnsBadRequest() throws Exception {
        // Given
        VerifyOtpRequest request = new VerifyOtpRequest(TEST_PHONE, TEST_OTP);
        
        when(authService.verifyOtp(any(VerifyOtpRequest.class)))
            .thenThrow(new BusinessException("OTP_EXPIRED", "OTP has expired", HttpStatus.BAD_REQUEST));

        // When & Then
        mockMvc.perform(post(BASE_URL + "/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("OTP_EXPIRED"));
    }

    @Test
    void verifyOtp_NullFields_ReturnsBadRequest() throws Exception {
        // Given
        VerifyOtpRequest request = new VerifyOtpRequest(null, null);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.phone").exists())
            .andExpect(jsonPath("$.errors.otpCode").exists());

        verify(authService, never()).verifyOtp(any());
    }

    @Test
    void refreshToken_ValidRequest_ReturnsNewAccessToken() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        RefreshTokenResponse response = new RefreshTokenResponse("new-access-token");
        
        when(authService.refreshAccessToken(any(RefreshTokenRequest.class))).thenReturn(response);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("new-access-token"));

        verify(authService).refreshAccessToken(any(RefreshTokenRequest.class));
    }

    @Test
    void refreshToken_InvalidToken_ReturnsUnauthorized() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-token");
        
        when(authService.refreshAccessToken(any(RefreshTokenRequest.class)))
            .thenThrow(new BusinessException("INVALID_REFRESH_TOKEN", "Invalid or expired refresh token", HttpStatus.UNAUTHORIZED));

        // When & Then
        mockMvc.perform(post(BASE_URL + "/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void refreshToken_NullToken_ReturnsBadRequest() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest(null);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.refreshToken").exists());

        verify(authService, never()).refreshAccessToken(any());
    }

    @Test
    void logout_ValidRequest_ReturnsNoContent() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        doNothing().when(authService).logout(any(RefreshTokenRequest.class));

        // When & Then
        mockMvc.perform(post(BASE_URL + "/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNoContent());

        verify(authService).logout(any(RefreshTokenRequest.class));
    }

    @Test
    void logout_InvalidToken_ReturnsUnauthorized() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-token");
        
        doThrow(new BusinessException("INVALID_REFRESH_TOKEN", "Invalid refresh token", HttpStatus.UNAUTHORIZED))
            .when(authService).logout(any(RefreshTokenRequest.class));

        // When & Then
        mockMvc.perform(post(BASE_URL + "/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }
}

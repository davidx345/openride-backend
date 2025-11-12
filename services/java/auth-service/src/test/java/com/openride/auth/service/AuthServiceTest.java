package com.openride.auth.service;

import com.openride.auth.config.AuthProperties;
import com.openride.auth.dto.*;
import com.openride.auth.entity.OtpRequest;
import com.openride.auth.repository.OtpRequestRepository;
import com.openride.commons.exception.BusinessException;
import com.openride.commons.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthService.
 * Tests OTP generation, verification, JWT token management, and refresh token flow.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private OtpRequestRepository otpRequestRepository;

    @Mock
    private SmsService smsService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private AuthProperties authProperties;

    @Mock
    private AuthProperties.OtpConfig otpConfig;

    @Mock
    private AuthProperties.JwtConfig jwtConfig;

    @InjectMocks
    private AuthService authService;

    private static final String TEST_PHONE = "+2348012345678";
    private static final String TEST_OTP = "123456";
    private static final String TEST_USER_ID = UUID.randomUUID().toString();
    private static final String TEST_JWT_SECRET = "test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm";
    private static final String USER_SERVICE_URL = "http://localhost:8082/api/v1/users";

    @BeforeEach
    void setUp() {
        // Setup auth properties mocks
        when(authProperties.getOtp()).thenReturn(otpConfig);
        when(authProperties.getJwt()).thenReturn(jwtConfig);
        
        when(otpConfig.getLength()).thenReturn(6);
        when(otpConfig.getExpirySeconds()).thenReturn(300);
        when(otpConfig.getMaxAttempts()).thenReturn(5);
        
        when(jwtConfig.getSecret()).thenReturn(TEST_JWT_SECRET);
        when(jwtConfig.getAccessTokenExpiryMs()).thenReturn(3600000L); // 1 hour
        when(jwtConfig.getRefreshTokenExpiryMs()).thenReturn(604800000L); // 7 days
        
        // Setup Redis template mock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // Initialize auth service with user service URL
        authService = new AuthService(
            otpRequestRepository,
            smsService,
            rateLimitService,
            restTemplate,
            redisTemplate,
            authProperties,
            USER_SERVICE_URL
        );
    }

    @Test
    void sendOtp_Success() {
        // Given
        SendOtpRequest request = new SendOtpRequest(TEST_PHONE);
        when(rateLimitService.allowOtpSend(TEST_PHONE)).thenReturn(true);
        when(otpRequestRepository.save(any(OtpRequest.class))).thenAnswer(i -> i.getArgument(0));

        // When
        SendOtpResponse response = authService.sendOtp(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).contains("OTP sent");
        assertThat(response.getExpiresIn()).isEqualTo(300);

        // Verify OTP was saved
        ArgumentCaptor<OtpRequest> otpCaptor = ArgumentCaptor.forClass(OtpRequest.class);
        verify(otpRequestRepository).save(otpCaptor.capture());
        OtpRequest savedOtp = otpCaptor.getValue();
        assertThat(savedOtp.getPhoneNumber()).isEqualTo(TEST_PHONE);
        assertThat(savedOtp.getOtpCode()).hasSize(6);
        assertThat(savedOtp.isVerified()).isFalse();
        assertThat(savedOtp.getAttempts()).isEqualTo(0);

        // Verify SMS was sent
        verify(smsService).sendOtp(eq(TEST_PHONE), anyString());
    }

    @Test
    void sendOtp_RateLimitExceeded_ThrowsException() {
        // Given
        SendOtpRequest request = new SendOtpRequest(TEST_PHONE);
        when(rateLimitService.allowOtpSend(TEST_PHONE)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> authService.sendOtp(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Too many OTP requests")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("RATE_LIMIT_EXCEEDED");

        // Verify no OTP was saved or SMS sent
        verify(otpRequestRepository, never()).save(any());
        verify(smsService, never()).sendOtp(anyString(), anyString());
    }

    @Test
    void verifyOtp_Success() {
        // Given
        VerifyOtpRequest request = new VerifyOtpRequest(TEST_PHONE, TEST_OTP);
        OtpRequest otpRequest = OtpRequest.builder()
            .id(UUID.randomUUID())
            .phoneNumber(TEST_PHONE)
            .otpCode(TEST_OTP)
            .verified(false)
            .attempts(0)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();

        UserInfo userInfo = new UserInfo(
            TEST_USER_ID,
            TEST_PHONE,
            "John Doe",
            null,
            "RIDER",
            "NONE",
            null,
            true
        );

        when(rateLimitService.allowOtpVerify(TEST_PHONE)).thenReturn(true);
        when(otpRequestRepository.findValidOtpByPhoneNumber(eq(TEST_PHONE), any(LocalDateTime.class)))
            .thenReturn(Optional.of(otpRequest));
        when(restTemplate.postForObject(anyString(), any(), eq(UserInfo.class)))
            .thenReturn(userInfo);
        when(otpRequestRepository.save(any(OtpRequest.class))).thenAnswer(i -> i.getArgument(0));

        // When
        AuthResponse response = authService.verifyOtp(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(response.getUser().getPhone()).isEqualTo(TEST_PHONE);

        // Verify OTP was marked as verified
        ArgumentCaptor<OtpRequest> otpCaptor = ArgumentCaptor.forClass(OtpRequest.class);
        verify(otpRequestRepository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().isVerified()).isTrue();

        // Verify refresh token was stored in Redis
        verify(valueOperations).set(
            eq("refresh_token:" + TEST_USER_ID),
            anyString(),
            eq(604800000L),
            eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void verifyOtp_RateLimitExceeded_ThrowsException() {
        // Given
        VerifyOtpRequest request = new VerifyOtpRequest(TEST_PHONE, TEST_OTP);
        when(rateLimitService.allowOtpVerify(TEST_PHONE)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> authService.verifyOtp(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Too many verification attempts")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("RATE_LIMIT_EXCEEDED");

        verify(otpRequestRepository, never()).findValidOtpByPhoneNumber(anyString(), any());
    }

    @Test
    void verifyOtp_OtpNotFound_ThrowsException() {
        // Given
        VerifyOtpRequest request = new VerifyOtpRequest(TEST_PHONE, TEST_OTP);
        when(rateLimitService.allowOtpVerify(TEST_PHONE)).thenReturn(true);
        when(otpRequestRepository.findValidOtpByPhoneNumber(eq(TEST_PHONE), any(LocalDateTime.class)))
            .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> authService.verifyOtp(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Invalid or expired OTP")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("OTP_INVALID");
    }

    @Test
    void verifyOtp_InvalidCode_ThrowsException() {
        // Given
        VerifyOtpRequest request = new VerifyOtpRequest(TEST_PHONE, "wrong-code");
        OtpRequest otpRequest = OtpRequest.builder()
            .id(UUID.randomUUID())
            .phoneNumber(TEST_PHONE)
            .otpCode(TEST_OTP)
            .verified(false)
            .attempts(2)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();

        when(rateLimitService.allowOtpVerify(TEST_PHONE)).thenReturn(true);
        when(otpRequestRepository.findValidOtpByPhoneNumber(eq(TEST_PHONE), any(LocalDateTime.class)))
            .thenReturn(Optional.of(otpRequest));
        when(otpRequestRepository.save(any(OtpRequest.class))).thenAnswer(i -> i.getArgument(0));

        // When & Then
        assertThatThrownBy(() -> authService.verifyOtp(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Invalid OTP code")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("OTP_INVALID");

        // Verify attempts were incremented
        ArgumentCaptor<OtpRequest> otpCaptor = ArgumentCaptor.forClass(OtpRequest.class);
        verify(otpRequestRepository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().getAttempts()).isEqualTo(3);
    }

    @Test
    void verifyOtp_MaxAttemptsExceeded_ThrowsException() {
        // Given
        VerifyOtpRequest request = new VerifyOtpRequest(TEST_PHONE, "wrong-code");
        OtpRequest otpRequest = OtpRequest.builder()
            .id(UUID.randomUUID())
            .phoneNumber(TEST_PHONE)
            .otpCode(TEST_OTP)
            .verified(false)
            .attempts(5)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();

        when(rateLimitService.allowOtpVerify(TEST_PHONE)).thenReturn(true);
        when(otpRequestRepository.findValidOtpByPhoneNumber(eq(TEST_PHONE), any(LocalDateTime.class)))
            .thenReturn(Optional.of(otpRequest));

        // When & Then
        assertThatThrownBy(() -> authService.verifyOtp(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Maximum verification attempts exceeded")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("OTP_MAX_ATTEMPTS");
    }

    @Test
    void verifyOtp_OtpExpired_ThrowsException() {
        // Given
        VerifyOtpRequest request = new VerifyOtpRequest(TEST_PHONE, TEST_OTP);
        OtpRequest otpRequest = OtpRequest.builder()
            .id(UUID.randomUUID())
            .phoneNumber(TEST_PHONE)
            .otpCode(TEST_OTP)
            .verified(false)
            .attempts(0)
            .expiresAt(LocalDateTime.now().minusMinutes(1)) // Already expired
            .build();

        when(rateLimitService.allowOtpVerify(TEST_PHONE)).thenReturn(true);
        when(otpRequestRepository.findValidOtpByPhoneNumber(eq(TEST_PHONE), any(LocalDateTime.class)))
            .thenReturn(Optional.of(otpRequest));

        // When & Then
        assertThatThrownBy(() -> authService.verifyOtp(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("OTP has expired")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("OTP_EXPIRED");
    }

    @Test
    void refreshAccessToken_Success() {
        // Given
        String refreshToken = JwtUtil.generateToken(TEST_USER_ID, "RIDER", 7 * 24 * 60 * 60 * 1000, TEST_JWT_SECRET);
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        
        when(valueOperations.get("refresh_token:" + TEST_USER_ID)).thenReturn(refreshToken);

        // When
        RefreshTokenResponse response = authService.refreshAccessToken(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getAccessToken()).isNotEqualTo(refreshToken);
    }

    @Test
    void refreshAccessToken_InvalidToken_ThrowsException() {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-token");

        // When & Then
        assertThatThrownBy(() -> authService.refreshAccessToken(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Invalid or expired refresh token")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void refreshAccessToken_TokenNotInRedis_ThrowsException() {
        // Given
        String refreshToken = JwtUtil.generateToken(TEST_USER_ID, "RIDER", 7 * 24 * 60 * 60 * 1000, TEST_JWT_SECRET);
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        
        when(valueOperations.get("refresh_token:" + TEST_USER_ID)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> authService.refreshAccessToken(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Invalid or expired refresh token")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void refreshAccessToken_TokenMismatch_ThrowsException() {
        // Given
        String refreshToken = JwtUtil.generateToken(TEST_USER_ID, "RIDER", 7 * 24 * 60 * 60 * 1000, TEST_JWT_SECRET);
        String differentToken = JwtUtil.generateToken(TEST_USER_ID, "RIDER", 7 * 24 * 60 * 60 * 1000, TEST_JWT_SECRET);
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        
        when(valueOperations.get("refresh_token:" + TEST_USER_ID)).thenReturn(differentToken);

        // When & Then
        assertThatThrownBy(() -> authService.refreshAccessToken(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Invalid or expired refresh token")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void logout_Success() {
        // Given
        String refreshToken = JwtUtil.generateToken(TEST_USER_ID, "RIDER", 7 * 24 * 60 * 60 * 1000, TEST_JWT_SECRET);
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        
        when(redisTemplate.delete("refresh_token:" + TEST_USER_ID)).thenReturn(true);

        // When
        authService.logout(request);

        // Then
        verify(redisTemplate).delete("refresh_token:" + TEST_USER_ID);
    }

    @Test
    void logout_InvalidToken_ThrowsException() {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-token");

        // When & Then
        assertThatThrownBy(() -> authService.logout(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Invalid refresh token")
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo("INVALID_REFRESH_TOKEN");

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void generateOtpCode_GeneratesValidCode() {
        // When
        String otpCode = authService.generateOtpCode();

        // Then
        assertThat(otpCode).hasSize(6);
        assertThat(otpCode).matches("\\d{6}");
    }

    @Test
    void generateOtpCode_GeneratesDifferentCodes() {
        // When
        String code1 = authService.generateOtpCode();
        String code2 = authService.generateOtpCode();
        String code3 = authService.generateOtpCode();

        // Then - at least one should be different (probability of all 3 being same is ~1 in a billion)
        assertThat(code1).isNotEqualTo(code2).or().isNotEqualTo(code3);
    }
}

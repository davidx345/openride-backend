package com.openride.auth.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openride.auth.dto.*;
import com.openride.auth.entity.OtpRequest;
import com.openride.auth.repository.OtpRequestRepository;
import com.openride.commons.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Auth Service.
 * Tests end-to-end authentication flows including OTP, JWT, and token refresh.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OtpRequestRepository otpRequestRepository;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private RedisTemplate<String, String> redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @Value("${app.auth.jwt.secret}")
    private String jwtSecret;

    private static final String BASE_URL = "/api/v1/auth";
    private static final String TEST_PHONE = "+2348012345678";
    private static final String TEST_USER_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        otpRequestRepository.deleteAll();
        
        // Setup Redis mock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void completeAuthFlow_SendOtpToVerifyToRefreshToLogout_Success() throws Exception {
        // Step 1: Send OTP
        SendOtpRequest sendOtpRequest = new SendOtpRequest(TEST_PHONE);
        
        MvcResult sendResult = mockMvc.perform(post(BASE_URL + "/send-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sendOtpRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.expiresIn").value(300))
            .andReturn();

        // Verify OTP was saved in database
        OtpRequest savedOtp = otpRequestRepository.findAll().get(0);
        assertThat(savedOtp).isNotNull();
        assertThat(savedOtp.getPhoneNumber()).isEqualTo(TEST_PHONE);
        assertThat(savedOtp.getOtpCode()).hasSize(6);
        assertThat(savedOtp.isVerified()).isFalse();

        // Step 2: Mock User Service response
        UserInfo mockUserInfo = new UserInfo(
            TEST_USER_ID,
            TEST_PHONE,
            "John Doe",
            null,
            "RIDER",
            "NONE",
            null,
            true
        );
        when(restTemplate.postForObject(anyString(), any(), eq(UserInfo.class)))
            .thenReturn(mockUserInfo);

        // Step 3: Verify OTP
        VerifyOtpRequest verifyOtpRequest = new VerifyOtpRequest(TEST_PHONE, savedOtp.getOtpCode());
        
        MvcResult verifyResult = mockMvc.perform(post(BASE_URL + "/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyOtpRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
            .andExpect(jsonPath("$.user.userId").value(TEST_USER_ID))
            .andExpect(jsonPath("$.user.phone").value(TEST_PHONE))
            .andReturn();

        AuthResponse authResponse = objectMapper.readValue(
            verifyResult.getResponse().getContentAsString(),
            AuthResponse.class
        );

        // Verify tokens are valid
        assertThat(authResponse.getAccessToken()).isNotBlank();
        assertThat(authResponse.getRefreshToken()).isNotBlank();
        assertThat(JwtUtil.extractUserId(authResponse.getAccessToken(), jwtSecret))
            .isEqualTo(TEST_USER_ID);

        // Verify OTP was marked as verified
        OtpRequest verifiedOtp = otpRequestRepository.findById(savedOtp.getId()).get();
        assertThat(verifiedOtp.isVerified()).isTrue();

        // Step 4: Mock Redis for refresh token validation
        when(valueOperations.get("refresh_token:" + TEST_USER_ID))
            .thenReturn(authResponse.getRefreshToken());

        // Step 5: Refresh access token
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(authResponse.getRefreshToken());
        
        MvcResult refreshResult = mockMvc.perform(post(BASE_URL + "/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andReturn();

        RefreshTokenResponse refreshResponse = objectMapper.readValue(
            refreshResult.getResponse().getContentAsString(),
            RefreshTokenResponse.class
        );

        // Verify new access token is different but valid
        assertThat(refreshResponse.getAccessToken()).isNotBlank();
        assertThat(refreshResponse.getAccessToken()).isNotEqualTo(authResponse.getAccessToken());
        assertThat(JwtUtil.extractUserId(refreshResponse.getAccessToken(), jwtSecret))
            .isEqualTo(TEST_USER_ID);

        // Step 6: Logout
        when(redisTemplate.delete("refresh_token:" + TEST_USER_ID)).thenReturn(true);
        
        mockMvc.perform(post(BASE_URL + "/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isNoContent());
    }

    @Test
    void sendOtp_MultipleTimes_SamePhone_CreatesMultipleOtps() throws Exception {
        // Given
        SendOtpRequest request = new SendOtpRequest(TEST_PHONE);

        // When - Send OTP 3 times
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(BASE_URL + "/send-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        }

        // Then - Should have 3 OTP records
        assertThat(otpRequestRepository.findAll()).hasSize(3);
    }

    @Test
    void verifyOtp_InvalidCode_IncrementsAttempts() throws Exception {
        // Given - Create OTP manually
        OtpRequest otpRequest = OtpRequest.builder()
            .phoneNumber(TEST_PHONE)
            .otpCode("123456")
            .verified(false)
            .attempts(0)
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
        otpRequestRepository.save(otpRequest);

        // When - Try with wrong code 3 times
        VerifyOtpRequest wrongRequest = new VerifyOtpRequest(TEST_PHONE, "999999");
        
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(BASE_URL + "/verify-otp")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(wrongRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("OTP_INVALID"));
        }

        // Then - Attempts should be incremented
        OtpRequest updated = otpRequestRepository.findById(otpRequest.getId()).get();
        assertThat(updated.getAttempts()).isEqualTo(3);
        assertThat(updated.isVerified()).isFalse();
    }

    @Test
    void verifyOtp_MaxAttemptsExceeded_ReturnsError() throws Exception {
        // Given - Create OTP with max attempts
        OtpRequest otpRequest = OtpRequest.builder()
            .phoneNumber(TEST_PHONE)
            .otpCode("123456")
            .verified(false)
            .attempts(5) // Max attempts
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build();
        otpRequestRepository.save(otpRequest);

        // When - Try to verify
        VerifyOtpRequest request = new VerifyOtpRequest(TEST_PHONE, "999999");
        
        mockMvc.perform(post(BASE_URL + "/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("OTP_MAX_ATTEMPTS"));
    }

    @Test
    void verifyOtp_ExpiredOtp_ReturnsError() throws Exception {
        // Given - Create expired OTP
        OtpRequest otpRequest = OtpRequest.builder()
            .phoneNumber(TEST_PHONE)
            .otpCode("123456")
            .verified(false)
            .attempts(0)
            .expiresAt(LocalDateTime.now().minusMinutes(1)) // Already expired
            .build();
        otpRequestRepository.save(otpRequest);

        // When
        VerifyOtpRequest request = new VerifyOtpRequest(TEST_PHONE, "123456");
        
        mockMvc.perform(post(BASE_URL + "/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("OTP_EXPIRED"));
    }

    @Test
    void refreshToken_InvalidToken_ReturnsError() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-jwt-token");

        // When & Then
        mockMvc.perform(post(BASE_URL + "/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void refreshToken_TokenNotInRedis_ReturnsError() throws Exception {
        // Given - Valid token but not in Redis
        String refreshToken = JwtUtil.generateToken(TEST_USER_ID, "RIDER", 604800000, jwtSecret);
        RefreshTokenRequest request = new RefreshTokenRequest(refreshToken);
        
        when(valueOperations.get("refresh_token:" + TEST_USER_ID)).thenReturn(null);

        // When & Then
        mockMvc.perform(post(BASE_URL + "/refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logout_InvalidToken_ReturnsError() throws Exception {
        // Given
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-jwt-token");

        // When & Then
        mockMvc.perform(post(BASE_URL + "/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }
}

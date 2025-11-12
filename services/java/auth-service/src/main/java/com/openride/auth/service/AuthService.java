package com.openride.auth.service;

import com.openride.auth.config.AuthProperties;
import com.openride.auth.dto.AuthResponse;
import com.openride.auth.dto.RefreshTokenResponse;
import com.openride.auth.dto.SendOtpResponse;
import com.openride.auth.entity.OtpRequest;
import com.openride.auth.repository.OtpRequestRepository;
import com.openride.commons.exception.BusinessException;
import com.openride.commons.exception.TechnicalException;
import com.openride.commons.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for authentication operations including OTP and JWT management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final OtpRequestRepository otpRequestRepository;
    private final SmsService smsService;
    private final RateLimitService rateLimitService;
    private final StringRedisTemplate redisTemplate;
    private final AuthProperties authProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    /**
     * Sends OTP to the specified phone number.
     *
     * @param phoneNumber phone number to send OTP to
     * @return SendOtpResponse with expiry information
     * @throws BusinessException if rate limit exceeded
     */
    @Transactional
    public SendOtpResponse sendOtp(String phoneNumber) {
        log.info("Sending OTP to phone: {}", phoneNumber);

        if (!rateLimitService.allowOtpSend(phoneNumber)) {
            throw new BusinessException(
                "RATE_LIMIT_EXCEEDED",
                "Too many OTP requests. Please try again later.",
                HttpStatus.TOO_MANY_REQUESTS
            );
        }

        String otpCode = generateOtpCode();
        LocalDateTime expiresAt = LocalDateTime.now()
            .plusSeconds(authProperties.getOtp().getExpirySeconds());

        OtpRequest otpRequest = OtpRequest.builder()
            .phoneNumber(phoneNumber)
            .otpCode(otpCode)
            .expiresAt(expiresAt)
            .verified(false)
            .attempts(0)
            .build();

        otpRequestRepository.save(otpRequest);

        boolean smsSent = smsService.sendOtp(phoneNumber, otpCode);
        
        if (!smsSent) {
            log.error("Failed to send SMS to {}", phoneNumber);
            throw new TechnicalException(
                "SMS_SEND_FAILED",
                "Failed to send OTP. Please try again."
            );
        }

        log.info("OTP sent successfully to {}", phoneNumber);

        return SendOtpResponse.builder()
            .message("OTP sent successfully")
            .expiresIn(authProperties.getOtp().getExpirySeconds())
            .build();
    }

    /**
     * Verifies OTP and creates user session with JWT tokens.
     *
     * @param phoneNumber phone number to verify
     * @param code OTP code to verify
     * @return AuthResponse with access and refresh tokens
     * @throws BusinessException if verification fails
     */
    @Transactional
    public AuthResponse verifyOtp(String phoneNumber, String code) {
        log.info("Verifying OTP for phone: {}", phoneNumber);

        if (!rateLimitService.allowOtpVerify(phoneNumber)) {
            throw new BusinessException(
                "RATE_LIMIT_EXCEEDED",
                "Too many verification attempts. Please try again later.",
                HttpStatus.TOO_MANY_REQUESTS
            );
        }

        OtpRequest otpRequest = otpRequestRepository
            .findValidOtpByPhoneNumber(phoneNumber, LocalDateTime.now())
            .orElseThrow(() -> new BusinessException(
                "OTP_INVALID",
                "Invalid or expired OTP",
                HttpStatus.UNAUTHORIZED
            ));

        if (otpRequest.isExpired()) {
            throw new BusinessException(
                "OTP_EXPIRED",
                "OTP has expired. Please request a new one.",
                HttpStatus.UNAUTHORIZED
            );
        }

        if (otpRequest.getAttempts() >= authProperties.getOtp().getMaxAttempts()) {
            throw new BusinessException(
                "OTP_MAX_ATTEMPTS",
                "Maximum verification attempts exceeded. Please request a new OTP.",
                HttpStatus.UNAUTHORIZED
            );
        }

        otpRequest.incrementAttempts();

        if (!otpRequest.getOtpCode().equals(code)) {
            otpRequestRepository.save(otpRequest);
            throw new BusinessException(
                "OTP_INVALID",
                "Invalid OTP code",
                HttpStatus.UNAUTHORIZED
            );
        }

        otpRequest.markAsVerified();
        otpRequestRepository.save(otpRequest);

        AuthResponse.UserInfo user = getOrCreateUser(phoneNumber);

        String accessToken = JwtUtil.generateAccessToken(
            user.getId().toString(),
            user.getRole(),
            authProperties.getJwt().getSecret(),
            authProperties.getJwt().getAccessTokenExpiryMs()
        );

        String refreshToken = generateRefreshToken(user.getId().toString());

        log.info("OTP verified successfully for phone: {}", phoneNumber);

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .user(user)
            .build();
    }

    /**
     * Refreshes access token using valid refresh token.
     *
     * @param refreshToken refresh token
     * @return RefreshTokenResponse with new access token
     * @throws BusinessException if refresh token is invalid
     */
    public RefreshTokenResponse refreshAccessToken(String refreshToken) {
        log.info("Refreshing access token");

        String userId = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + refreshToken);

        if (userId == null) {
            throw new BusinessException(
                "INVALID_REFRESH_TOKEN",
                "Invalid or expired refresh token",
                HttpStatus.UNAUTHORIZED
            );
        }

        AuthResponse.UserInfo user = getUserById(userId);

        String newAccessToken = JwtUtil.generateAccessToken(
            user.getId().toString(),
            user.getRole(),
            authProperties.getJwt().getSecret(),
            authProperties.getJwt().getAccessTokenExpiryMs()
        );

        log.info("Access token refreshed for user: {}", userId);

        return RefreshTokenResponse.builder()
            .accessToken(newAccessToken)
            .build();
    }

    /**
     * Logs out user by invalidating refresh token.
     *
     * @param refreshToken refresh token to invalidate
     */
    public void logout(String refreshToken) {
        log.info("Logging out user");
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
    }

    /**
     * Generates a random OTP code.
     *
     * @return OTP code as string
     */
    private String generateOtpCode() {
        int length = authProperties.getOtp().getLength();
        Random random = new Random();
        StringBuilder otp = new StringBuilder();

        for (int i = 0; i < length; i++) {
            otp.append(random.nextInt(10));
        }

        return otp.toString();
    }

    /**
     * Generates a refresh token and stores it in Redis.
     *
     * @param userId user ID
     * @return refresh token
     */
    private String generateRefreshToken(String userId) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
            REFRESH_TOKEN_PREFIX + token,
            userId,
            authProperties.getJwt().getRefreshTokenExpiryMs(),
            TimeUnit.MILLISECONDS
        );
        return token;
    }

    /**
     * Gets or creates user via User Service.
     *
     * @param phoneNumber phone number
     * @return user information
     */
    private AuthResponse.UserInfo getOrCreateUser(String phoneNumber) {
        try {
            String userServiceUrl = System.getenv("USER_SERVICE_URL");
            if (userServiceUrl == null) {
                userServiceUrl = "http://localhost:8082/api";
            }

            Map<String, String> request = new HashMap<>();
            request.put("phone", phoneNumber);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                userServiceUrl + "/v1/users",
                request,
                Map.class
            );

            return mapToUserInfo(response);

        } catch (Exception e) {
            log.error("Failed to get/create user: {}", e.getMessage(), e);
            throw new TechnicalException(
                "USER_SERVICE_ERROR",
                "Failed to process user information"
            );
        }
    }

    /**
     * Gets user by ID from User Service.
     *
     * @param userId user ID
     * @return user information
     */
    private AuthResponse.UserInfo getUserById(String userId) {
        try {
            String userServiceUrl = System.getenv("USER_SERVICE_URL");
            if (userServiceUrl == null) {
                userServiceUrl = "http://localhost:8082/api";
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                userServiceUrl + "/v1/users/" + userId,
                Map.class
            );

            return mapToUserInfo(response);

        } catch (Exception e) {
            log.error("Failed to get user by ID: {}", e.getMessage(), e);
            throw new TechnicalException(
                "USER_SERVICE_ERROR",
                "Failed to retrieve user information"
            );
        }
    }

    /**
     * Maps User Service response to UserInfo DTO.
     *
     * @param response response map from User Service
     * @return UserInfo object
     */
    private AuthResponse.UserInfo mapToUserInfo(Map<String, Object> response) {
        return AuthResponse.UserInfo.builder()
            .id(UUID.fromString((String) response.get("id")))
            .phone((String) response.get("phone"))
            .role((String) response.get("role"))
            .fullName((String) response.get("fullName"))
            .email((String) response.get("email"))
            .build();
    }
}

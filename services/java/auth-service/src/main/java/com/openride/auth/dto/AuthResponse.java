package com.openride.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for successful OTP verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private UserInfo user;
    private boolean isNewUser;

    /**
     * Inner class representing user information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private UUID id;
        private String phone;
        private String role;
        private String fullName;
        private String email;
        private String kycStatus;
        private BigDecimal rating;
        private boolean isActive;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}

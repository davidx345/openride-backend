package com.openride.user.dto;

import com.openride.user.enums.KycStatus;
import com.openride.user.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for user information.
 * Excludes sensitive fields like encrypted BVN.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String phone;
    private String fullName;
    private String email;
    private UserRole role;
    private KycStatus kycStatus;
    private BigDecimal rating;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private DriverProfileResponse driverProfile;

    /**
     * Nested DTO for driver profile information.
     * Excludes encrypted sensitive data.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriverProfileResponse {
        private UUID id;
        private String licensePhotoUrl;
        private String vehiclePhotoUrl;
        private String kycNotes;
        private Integer totalTrips;
        private BigDecimal totalEarnings;
    }
}

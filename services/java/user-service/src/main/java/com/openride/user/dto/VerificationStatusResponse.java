package com.openride.user.dto;

import com.openride.user.enums.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for verification status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationStatusResponse {

    /**
     * Overall KYC status.
     */
    private KycStatus kycStatus;

    /**
     * Human-readable status message.
     */
    private String statusMessage;

    /**
     * Identity verification status.
     */
    private IdentityStatus identity;

    /**
     * Affiliation verification status.
     */
    private AffiliationStatus affiliation;

    /**
     * Captain verification status (null if not a captain).
     */
    private CaptainStatus captain;

    /**
     * Whether user can book rides (Passenger functionality).
     */
    private boolean canBookRides;

    /**
     * Whether user can offer rides (Captain functionality).
     */
    private boolean canOfferRides;

    /**
     * Next step in verification process.
     */
    private String nextStep;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdentityStatus {
        private boolean submitted;
        private boolean verified;
        private boolean ninVerified;
        private BigDecimal selfieMatchScore;
        private String rejectionReason;
        private LocalDateTime verifiedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AffiliationStatus {
        private boolean submitted;
        private boolean verified;
        private String affiliationType;
        private String organizationName;
        private String verificationMethod;
        private boolean emailVerified;
        private String rejectionReason;
        private LocalDateTime verifiedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaptainStatus {
        private boolean submitted;
        private boolean verified;
        private boolean licenseVerified;
        private String licenseExpiryDate;
        private boolean hasVehicle;
        private String rejectionReason;
        private LocalDateTime verifiedAt;
    }
}

package com.openride.user.entity;

import com.openride.user.enums.KycStatus;
import com.openride.user.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a user in the OpenRide platform.
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "phone", nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "email", length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private UserRole role = UserRole.PASSENGER;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.UNVERIFIED;

    @Column(name = "rating", precision = 2, scale = 1)
    @Builder.Default
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "selfie_url", columnDefinition = "TEXT")
    private String selfieUrl;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Checks if user is a captain (driver).
     *
     * @return true if role is CAPTAIN, false otherwise
     */
    public boolean isCaptain() {
        return UserRole.CAPTAIN.equals(this.role);
    }

    /**
     * Checks if user is an admin.
     *
     * @return true if role is ADMIN, false otherwise
     */
    public boolean isAdmin() {
        return UserRole.ADMIN.equals(this.role);
    }

    /**
     * Checks if user has completed identity verification.
     *
     * @return true if identity is verified, false otherwise
     */
    public boolean isIdentityVerified() {
        return this.kycStatus == KycStatus.IDENTITY_VERIFIED 
            || this.kycStatus == KycStatus.FULLY_VERIFIED
            || this.kycStatus == KycStatus.CAPTAIN_PENDING
            || this.kycStatus == KycStatus.CAPTAIN_VERIFIED;
    }

    /**
     * Checks if user is fully verified (can book rides).
     *
     * @return true if fully verified, false otherwise
     */
    public boolean isFullyVerified() {
        return this.kycStatus == KycStatus.FULLY_VERIFIED
            || this.kycStatus == KycStatus.CAPTAIN_PENDING
            || this.kycStatus == KycStatus.CAPTAIN_VERIFIED;
    }

    /**
     * Checks if user is captain verified (can offer rides).
     *
     * @return true if captain verified, false otherwise
     */
    public boolean isCaptainVerified() {
        return KycStatus.CAPTAIN_VERIFIED.equals(this.kycStatus);
    }

    /**
     * Upgrades user to captain role.
     */
    public void upgradeToCaptain() {
        this.role = UserRole.CAPTAIN;
    }

    /**
     * Updates KYC status.
     *
     * @param newStatus new KYC status
     */
    public void updateKycStatus(KycStatus newStatus) {
        this.kycStatus = newStatus;
    }
}

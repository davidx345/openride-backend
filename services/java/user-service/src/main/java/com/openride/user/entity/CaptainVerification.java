package com.openride.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for storing Captain (driver) specific verification data.
 * Required for Captains to go online and offer rides.
 * This includes driver's license verification via Dojah/FRSC.
 */
@Entity
@Table(name = "captain_verifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CaptainVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Driver's license number (encrypted for security).
     */
    @Column(name = "license_number_encrypted", columnDefinition = "TEXT")
    private String licenseNumberEncrypted;

    /**
     * URL to uploaded driver's license image.
     */
    @Column(name = "license_photo_url", columnDefinition = "TEXT")
    private String licensePhotoUrl;

    /**
     * Whether license has been verified via Dojah/FRSC API.
     */
    @Column(name = "license_verified", nullable = false)
    @Builder.Default
    private Boolean licenseVerified = false;

    /**
     * Driver's license expiry date.
     */
    @Column(name = "license_expiry_date")
    private LocalDate licenseExpiryDate;

    /**
     * Reference ID from Dojah API for audit trail.
     */
    @Column(name = "dojah_reference_id", length = 100)
    private String dojahReferenceId;

    /**
     * Whether Captain verification is complete and approved.
     */
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    /**
     * Reason for rejection if verification failed.
     */
    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    /**
     * Timestamp when verification was completed.
     */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Marks captain verification as complete.
     */
    public void markAsVerified() {
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
    }

    /**
     * Marks captain verification as rejected with reason.
     *
     * @param reason rejection reason
     */
    public void markAsRejected(String reason) {
        this.isVerified = false;
        this.rejectionReason = reason;
    }

    /**
     * Checks if driver's license is expired.
     *
     * @return true if license is expired or expiry date is null
     */
    public boolean isLicenseExpired() {
        return licenseExpiryDate == null || LocalDate.now().isAfter(licenseExpiryDate);
    }
}

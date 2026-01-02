package com.openride.user.entity;

import com.openride.user.enums.GovernmentIdType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for storing identity verification data.
 * This includes NIN verification and government ID verification via Dojah.
 * Required for ALL users (both Passengers and Captains) to book/offer rides.
 */
@Entity
@Table(name = "identity_verifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class IdentityVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Nigerian National Identification Number (11 digits).
     * Stored encrypted for security.
     */
    @Column(name = "nin_encrypted", columnDefinition = "TEXT")
    private String ninEncrypted;

    /**
     * Whether NIN has been verified via Dojah API.
     */
    @Column(name = "nin_verified", nullable = false)
    @Builder.Default
    private Boolean ninVerified = false;

    /**
     * Type of government-issued ID uploaded.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "government_id_type")
    private GovernmentIdType governmentIdType;

    /**
     * URL to uploaded government ID image (stored in cloud storage).
     */
    @Column(name = "government_id_url", columnDefinition = "TEXT")
    private String governmentIdUrl;

    /**
     * URL to user's selfie for face matching.
     */
    @Column(name = "selfie_url", columnDefinition = "TEXT")
    private String selfieUrl;

    /**
     * Score from Dojah's selfie-to-ID face matching (0.00 to 1.00).
     * Typically require >= 0.70 for verification.
     */
    @Column(name = "selfie_match_score", precision = 3, scale = 2)
    private BigDecimal selfieMatchScore;

    /**
     * Reference ID from Dojah API for audit trail.
     */
    @Column(name = "dojah_reference_id", length = 100)
    private String dojahReferenceId;

    /**
     * Whether identity verification is complete and approved.
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
     * Marks identity as verified.
     */
    public void markAsVerified() {
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
    }

    /**
     * Marks identity as rejected with reason.
     *
     * @param reason rejection reason
     */
    public void markAsRejected(String reason) {
        this.isVerified = false;
        this.rejectionReason = reason;
    }
}

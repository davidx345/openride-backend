package com.openride.user.entity;

import com.openride.user.enums.AffiliationType;
import com.openride.user.enums.VerificationMethod;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for storing affiliation (work/student) verification data.
 * Required for ALL users (both Passengers and Captains) to book/offer rides.
 * 
 * Users must verify they are either a STUDENT or an EMPLOYEE using:
 * - ID_CARD: Upload work/student ID card
 * - EMAIL: Verify work/school email address
 */
@Entity
@Table(name = "affiliation_verifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AffiliationVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Type of affiliation: STUDENT or EMPLOYEE.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "affiliation_type", nullable = false)
    private AffiliationType affiliationType;

    /**
     * Name of organization (company name or school name).
     */
    @Column(name = "organization_name", length = 200)
    private String organizationName;

    /**
     * Method used for verification: ID_CARD or EMAIL.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", nullable = false)
    private VerificationMethod verificationMethod;

    /**
     * URL to uploaded work/student ID card image.
     * Used when verification_method is ID_CARD.
     */
    @Column(name = "id_card_url", columnDefinition = "TEXT")
    private String idCardUrl;

    /**
     * Work/school email address for verification.
     * Used when verification_method is EMAIL.
     */
    @Column(name = "verification_email", length = 255)
    private String verificationEmail;

    /**
     * Whether email has been verified.
     */
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    /**
     * Token sent to email for verification (stored hashed).
     */
    @Column(name = "email_verification_token", length = 255)
    private String emailVerificationToken;

    /**
     * Expiry time for email verification token.
     */
    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    /**
     * Whether affiliation verification is complete and approved.
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
     * Marks affiliation as verified.
     */
    public void markAsVerified() {
        this.isVerified = true;
        this.verifiedAt = LocalDateTime.now();
    }

    /**
     * Marks email as verified.
     */
    public void markEmailAsVerified() {
        this.emailVerified = true;
        this.markAsVerified();
    }

    /**
     * Marks affiliation as rejected with reason.
     *
     * @param reason rejection reason
     */
    public void markAsRejected(String reason) {
        this.isVerified = false;
        this.rejectionReason = reason;
    }

    /**
     * Generates and sets email verification token with expiry.
     *
     * @param token the verification token
     * @param expiryHours hours until token expires
     */
    public void setVerificationToken(String token, int expiryHours) {
        this.emailVerificationToken = token;
        this.tokenExpiresAt = LocalDateTime.now().plusHours(expiryHours);
    }

    /**
     * Checks if email verification token is still valid.
     *
     * @return true if token is not expired
     */
    public boolean isTokenValid() {
        return tokenExpiresAt != null && LocalDateTime.now().isBefore(tokenExpiresAt);
    }
}

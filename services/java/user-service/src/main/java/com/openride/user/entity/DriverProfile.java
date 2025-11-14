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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing driver-specific profile information.
 * Contains encrypted sensitive data and driver statistics.
 */
@Entity
@Table(name = "driver_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class DriverProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "bvn_encrypted", columnDefinition = "TEXT")
    private String bvnEncrypted;

    @Column(name = "license_number_encrypted", columnDefinition = "TEXT")
    private String licenseNumberEncrypted;

    @Column(name = "license_photo_url", columnDefinition = "TEXT")
    private String licensePhotoUrl;

    @Column(name = "vehicle_photo_url", columnDefinition = "TEXT")
    private String vehiclePhotoUrl;

    @Column(name = "kyc_notes", columnDefinition = "TEXT")
    private String kycNotes;

    @Column(name = "total_trips", nullable = false)
    @Builder.Default
    private Integer totalTrips = 0;

    @Column(name = "total_earnings", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalEarnings = BigDecimal.ZERO;

    // Phase 1.4: Enhanced driver metrics
    @Column(name = "rating_avg", precision = 3, scale = 2)
    private BigDecimal ratingAvg;

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private Integer ratingCount = 0;

    @Column(name = "cancellation_rate", precision = 5, scale = 2)
    private BigDecimal cancellationRate;

    @Column(name = "completed_trips", nullable = false)
    @Builder.Default
    private Integer completedTrips = 0;

    @Column(name = "cancelled_trips", nullable = false)
    @Builder.Default
    private Integer cancelledTrips = 0;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Increments the total trip count.
     */
    public void incrementTripCount() {
        this.totalTrips++;
    }

    /**
     * Increments the completed trip count.
     */
    public void incrementCompletedTrips() {
        this.completedTrips++;
        updateCancellationRate();
    }

    /**
     * Increments the cancelled trip count.
     */
    public void incrementCancelledTrips() {
        this.cancelledTrips++;
        updateCancellationRate();
    }

    /**
     * Updates driver rating with new rating value.
     *
     * @param newRating the new rating value (0-5)
     */
    public void updateRating(BigDecimal newRating) {
        if (newRating == null || newRating.compareTo(BigDecimal.ZERO) < 0 
                || newRating.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        }

        if (this.ratingAvg == null || this.ratingCount == 0) {
            this.ratingAvg = newRating;
            this.ratingCount = 1;
        } else {
            // Calculate new average: (old_avg * old_count + new_rating) / (old_count + 1)
            BigDecimal totalRating = this.ratingAvg
                    .multiply(BigDecimal.valueOf(this.ratingCount))
                    .add(newRating);
            this.ratingCount++;
            this.ratingAvg = totalRating
                    .divide(BigDecimal.valueOf(this.ratingCount), 2, BigDecimal.ROUND_HALF_UP);
        }
    }

    /**
     * Recalculates cancellation rate based on completed and cancelled trips.
     */
    private void updateCancellationRate() {
        int totalTripsCompleted = this.completedTrips + this.cancelledTrips;
        if (totalTripsCompleted > 0) {
            this.cancellationRate = BigDecimal.valueOf(this.cancelledTrips)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(totalTripsCompleted), 2, BigDecimal.ROUND_HALF_UP);
        } else {
            this.cancellationRate = BigDecimal.ZERO;
        }
    }

    /**
     * Adds earnings to the total.
     *
     * @param amount amount to add
     */
    public void addEarnings(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            this.totalEarnings = this.totalEarnings.add(amount);
        }
    }
}

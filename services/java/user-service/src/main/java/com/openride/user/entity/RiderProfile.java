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
 * Entity representing rider-specific profile information.
 * Contains trip metrics and behavior tracking for fraud detection and personalization.
 */
@Entity
@Table(name = "rider_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class RiderProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "completed_trips", nullable = false)
    @Builder.Default
    private Integer completedTrips = 0;

    @Column(name = "cancelled_trips", nullable = false)
    @Builder.Default
    private Integer cancelledTrips = 0;

    @Column(name = "no_show_count", nullable = false)
    @Builder.Default
    private Integer noShowCount = 0;

    @Column(name = "total_spent", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal totalSpent = BigDecimal.ZERO;

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "rating_count", nullable = false)
    @Builder.Default
    private Integer ratingCount = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Increments the completed trip count.
     */
    public void incrementCompletedTrips() {
        this.completedTrips++;
    }

    /**
     * Increments the cancelled trip count.
     */
    public void incrementCancelledTrips() {
        this.cancelledTrips++;
    }

    /**
     * Increments the no-show count.
     */
    public void incrementNoShowCount() {
        this.noShowCount++;
    }

    /**
     * Adds spending amount to total spent.
     *
     * @param amount amount spent on trip
     */
    public void addSpending(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            this.totalSpent = this.totalSpent.add(amount);
        }
    }

    /**
     * Updates rider rating with new rating value from driver.
     *
     * @param newRating the new rating value (0-5)
     */
    public void updateRating(BigDecimal newRating) {
        if (newRating == null || newRating.compareTo(BigDecimal.ZERO) < 0
                || newRating.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        }

        if (this.averageRating == null || this.ratingCount == 0) {
            this.averageRating = newRating;
            this.ratingCount = 1;
        } else {
            // Calculate new average: (old_avg * old_count + new_rating) / (old_count + 1)
            BigDecimal totalRating = this.averageRating
                    .multiply(BigDecimal.valueOf(this.ratingCount))
                    .add(newRating);
            this.ratingCount++;
            this.averageRating = totalRating
                    .divide(BigDecimal.valueOf(this.ratingCount), 2, BigDecimal.ROUND_HALF_UP);
        }
    }

    /**
     * Calculates cancellation rate percentage.
     *
     * @return cancellation rate (0-100)
     */
    public BigDecimal getCancellationRate() {
        int totalTrips = this.completedTrips + this.cancelledTrips;
        if (totalTrips == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(this.cancelledTrips)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalTrips), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Calculates no-show rate percentage.
     *
     * @return no-show rate (0-100)
     */
    public BigDecimal getNoShowRate() {
        int totalTrips = this.completedTrips + this.cancelledTrips + this.noShowCount;
        if (totalTrips == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(this.noShowCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalTrips), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Checks if rider is high-risk based on metrics.
     *
     * @return true if rider exhibits high-risk behavior
     */
    public boolean isHighRisk() {
        // High risk if: cancellation rate > 30% OR no-show rate > 20%
        return getCancellationRate().compareTo(BigDecimal.valueOf(30)) > 0
                || getNoShowRate().compareTo(BigDecimal.valueOf(20)) > 0;
    }
}

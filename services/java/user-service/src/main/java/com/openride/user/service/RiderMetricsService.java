package com.openride.user.service;

import com.openride.user.entity.RiderProfile;
import com.openride.user.repository.RiderProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service for managing rider profile metrics and behavior tracking.
 * Handles trip counts, spending, ratings, and fraud detection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RiderMetricsService {

    private final RiderProfileRepository riderProfileRepository;

    /**
     * Records a completed trip for a rider.
     *
     * @param riderId the rider's user ID
     * @param amountSpent amount spent on the trip
     */
    @Transactional
    public void recordCompletedTrip(UUID riderId, BigDecimal amountSpent) {
        RiderProfile profile = riderProfileRepository.findByUserId(riderId)
                .orElseThrow(() -> new IllegalArgumentException("Rider profile not found for user: " + riderId));

        profile.incrementCompletedTrips();
        if (amountSpent != null && amountSpent.compareTo(BigDecimal.ZERO) > 0) {
            profile.addSpending(amountSpent);
        }

        riderProfileRepository.save(profile);
        log.info("Recorded completed trip for rider: {}, total completed: {}, total spent: {}",
                riderId, profile.getCompletedTrips(), profile.getTotalSpent());
    }

    /**
     * Records a cancelled trip for a rider.
     *
     * @param riderId the rider's user ID
     */
    @Transactional
    public void recordCancelledTrip(UUID riderId) {
        RiderProfile profile = riderProfileRepository.findByUserId(riderId)
                .orElseThrow(() -> new IllegalArgumentException("Rider profile not found for user: " + riderId));

        profile.incrementCancelledTrips();

        riderProfileRepository.save(profile);
        log.warn("Recorded cancelled trip for rider: {}, total cancelled: {}, cancellation rate: {}%",
                riderId, profile.getCancelledTrips(), profile.getCancellationRate());
    }

    /**
     * Records a no-show for a rider.
     *
     * @param riderId the rider's user ID
     */
    @Transactional
    public void recordNoShow(UUID riderId) {
        RiderProfile profile = riderProfileRepository.findByUserId(riderId)
                .orElseThrow(() -> new IllegalArgumentException("Rider profile not found for user: " + riderId));

        profile.incrementNoShowCount();

        RiderProfile saved = riderProfileRepository.save(profile);
        
        if (saved.isHighRisk()) {
            log.warn("Rider {} is now HIGH RISK - No-show count: {}, No-show rate: {}%",
                    riderId, saved.getNoShowCount(), saved.getNoShowRate());
        } else {
            log.warn("Recorded no-show for rider: {}, total no-shows: {}, no-show rate: {}%",
                    riderId, saved.getNoShowCount(), saved.getNoShowRate());
        }
    }

    /**
     * Updates rider rating with a new rating from driver.
     *
     * @param riderId the rider's user ID
     * @param rating the new rating value (0-5)
     */
    @Transactional
    public void updateRiderRating(UUID riderId, BigDecimal rating) {
        if (rating == null || rating.compareTo(BigDecimal.ZERO) < 0
                || rating.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        }

        RiderProfile profile = riderProfileRepository.findByUserId(riderId)
                .orElseThrow(() -> new IllegalArgumentException("Rider profile not found for user: " + riderId));

        profile.updateRating(rating);

        riderProfileRepository.save(profile);
        log.info("Updated rating for rider: {}, new average: {}, count: {}",
                riderId, profile.getAverageRating(), profile.getRatingCount());
    }

    /**
     * Gets rider metrics for display.
     *
     * @param riderId the rider's user ID
     * @return rider profile with metrics
     */
    @Transactional(readOnly = true)
    public RiderProfile getRiderMetrics(UUID riderId) {
        return riderProfileRepository.findByUserId(riderId)
                .orElseThrow(() -> new IllegalArgumentException("Rider profile not found for user: " + riderId));
    }

    /**
     * Checks if rider is high-risk.
     *
     * @param riderId the rider's user ID
     * @return true if rider is high-risk
     */
    @Transactional(readOnly = true)
    public boolean isHighRiskRider(UUID riderId) {
        RiderProfile profile = riderProfileRepository.findByUserId(riderId)
                .orElseThrow(() -> new IllegalArgumentException("Rider profile not found for user: " + riderId));

        return profile.isHighRisk();
    }

    /**
     * Gets count of high-risk riders in the system.
     *
     * @return count of high-risk riders
     */
    @Transactional(readOnly = true)
    public long getHighRiskRiderCount() {
        return riderProfileRepository.countHighRiskRiders();
    }
}

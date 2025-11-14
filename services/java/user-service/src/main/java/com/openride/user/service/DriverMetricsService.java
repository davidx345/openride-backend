package com.openride.user.service;

import com.openride.user.entity.DriverProfile;
import com.openride.user.repository.DriverProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service for managing driver profile metrics and statistics.
 * Handles rating calculations, trip counts, and verification status.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DriverMetricsService {

    private final DriverProfileRepository driverProfileRepository;

    /**
     * Records a completed trip for a driver.
     *
     * @param driverId the driver's user ID
     */
    @Transactional
    public void recordCompletedTrip(UUID driverId) {
        DriverProfile profile = driverProfileRepository.findByUserId(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver profile not found for user: " + driverId));

        profile.incrementCompletedTrips();
        profile.incrementTripCount();

        driverProfileRepository.save(profile);
        log.info("Recorded completed trip for driver: {}, total completed: {}", 
                driverId, profile.getCompletedTrips());
    }

    /**
     * Records a cancelled trip for a driver.
     *
     * @param driverId the driver's user ID
     */
    @Transactional
    public void recordCancelledTrip(UUID driverId) {
        DriverProfile profile = driverProfileRepository.findByUserId(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver profile not found for user: " + driverId));

        profile.incrementCancelledTrips();

        driverProfileRepository.save(profile);
        log.warn("Recorded cancelled trip for driver: {}, total cancelled: {}, cancellation rate: {}%",
                driverId, profile.getCancelledTrips(), profile.getCancellationRate());
    }

    /**
     * Updates driver rating with a new rating.
     *
     * @param driverId the driver's user ID
     * @param rating the new rating value (0-5)
     */
    @Transactional
    public void updateDriverRating(UUID driverId, BigDecimal rating) {
        if (rating == null || rating.compareTo(BigDecimal.ZERO) < 0 
                || rating.compareTo(BigDecimal.valueOf(5)) > 0) {
            throw new IllegalArgumentException("Rating must be between 0 and 5");
        }

        DriverProfile profile = driverProfileRepository.findByUserId(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver profile not found for user: " + driverId));

        profile.updateRating(rating);

        driverProfileRepository.save(profile);
        log.info("Updated rating for driver: {}, new average: {}, count: {}",
                driverId, profile.getRatingAvg(), profile.getRatingCount());
    }

    /**
     * Verifies a driver profile (admin action).
     *
     * @param driverId the driver's user ID
     * @return updated driver profile
     */
    @Transactional
    public DriverProfile verifyDriver(UUID driverId) {
        DriverProfile profile = driverProfileRepository.findByUserId(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver profile not found for user: " + driverId));

        profile.setIsVerified(true);

        DriverProfile saved = driverProfileRepository.save(profile);
        log.info("Verified driver: {}", driverId);

        return saved;
    }

    /**
     * Unverifies a driver profile (admin action).
     *
     * @param driverId the driver's user ID
     * @return updated driver profile
     */
    @Transactional
    public DriverProfile unverifyDriver(UUID driverId) {
        DriverProfile profile = driverProfileRepository.findByUserId(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver profile not found for user: " + driverId));

        profile.setIsVerified(false);

        DriverProfile saved = driverProfileRepository.save(profile);
        log.warn("Unverified driver: {}", driverId);

        return saved;
    }

    /**
     * Gets driver metrics for display.
     *
     * @param driverId the driver's user ID
     * @return driver profile with metrics
     */
    @Transactional(readOnly = true)
    public DriverProfile getDriverMetrics(UUID driverId) {
        return driverProfileRepository.findByUserId(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver profile not found for user: " + driverId));
    }
}

package com.openride.user.repository;

import com.openride.user.entity.RiderProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for RiderProfile entity.
 */
@Repository
public interface RiderProfileRepository extends JpaRepository<RiderProfile, UUID> {

    /**
     * Find rider profile by user ID.
     *
     * @param userId the user ID
     * @return optional rider profile
     */
    Optional<RiderProfile> findByUserId(UUID userId);

    /**
     * Check if rider profile exists for user.
     *
     * @param userId the user ID
     * @return true if exists
     */
    boolean existsByUserId(UUID userId);

    /**
     * Count high-risk riders (cancellation rate > 30% OR no-show rate > 20%).
     *
     * @return count of high-risk riders
     */
    @Query("""
            SELECT COUNT(rp) FROM RiderProfile rp
            WHERE (rp.cancelledTrips * 100.0 / NULLIF(rp.completedTrips + rp.cancelledTrips, 0) > 30)
               OR (rp.noShowCount * 100.0 / NULLIF(rp.completedTrips + rp.cancelledTrips + rp.noShowCount, 0) > 20)
            """)
    long countHighRiskRiders();
}

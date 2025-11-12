package com.openride.user.repository;

import com.openride.user.entity.DriverProfile;
import com.openride.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for DriverProfile entity operations.
 */
@Repository
public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {

    /**
     * Finds a driver profile by user ID.
     *
     * @param userId user ID
     * @return optional driver profile
     */
    Optional<DriverProfile> findByUserId(UUID userId);

    /**
     * Finds a driver profile by user entity.
     *
     * @param user user entity
     * @return optional driver profile
     */
    Optional<DriverProfile> findByUser(User user);

    /**
     * Checks if a driver profile exists for a user.
     *
     * @param user user entity
     * @return true if exists, false otherwise
     */
    boolean existsByUser(User user);
}

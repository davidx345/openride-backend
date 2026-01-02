package com.openride.user.repository;

import com.openride.user.entity.IdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for IdentityVerification entity.
 */
@Repository
public interface IdentityVerificationRepository extends JpaRepository<IdentityVerification, UUID> {

    /**
     * Finds identity verification by user ID.
     *
     * @param userId user ID
     * @return optional identity verification
     */
    Optional<IdentityVerification> findByUserId(UUID userId);

    /**
     * Checks if user has identity verification record.
     *
     * @param userId user ID
     * @return true if exists
     */
    boolean existsByUserId(UUID userId);

    /**
     * Finds verified identity by user ID.
     *
     * @param userId user ID
     * @return optional verified identity
     */
    Optional<IdentityVerification> findByUserIdAndIsVerifiedTrue(UUID userId);
}

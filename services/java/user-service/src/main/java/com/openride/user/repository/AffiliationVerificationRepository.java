package com.openride.user.repository;

import com.openride.user.entity.AffiliationVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AffiliationVerification entity.
 */
@Repository
public interface AffiliationVerificationRepository extends JpaRepository<AffiliationVerification, UUID> {

    /**
     * Finds affiliation verification by user ID.
     *
     * @param userId user ID
     * @return optional affiliation verification
     */
    Optional<AffiliationVerification> findByUserId(UUID userId);

    /**
     * Checks if user has affiliation verification record.
     *
     * @param userId user ID
     * @return true if exists
     */
    boolean existsByUserId(UUID userId);

    /**
     * Finds affiliation by email verification token.
     *
     * @param token verification token
     * @return optional affiliation verification
     */
    Optional<AffiliationVerification> findByEmailVerificationToken(String token);

    /**
     * Finds verified affiliation by user ID.
     *
     * @param userId user ID
     * @return optional verified affiliation
     */
    Optional<AffiliationVerification> findByUserIdAndIsVerifiedTrue(UUID userId);
}

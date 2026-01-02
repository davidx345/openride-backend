package com.openride.user.repository;

import com.openride.user.entity.CaptainVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for CaptainVerification entity.
 */
@Repository
public interface CaptainVerificationRepository extends JpaRepository<CaptainVerification, UUID> {

    /**
     * Finds captain verification by user ID.
     *
     * @param userId user ID
     * @return optional captain verification
     */
    Optional<CaptainVerification> findByUserId(UUID userId);

    /**
     * Checks if user has captain verification record.
     *
     * @param userId user ID
     * @return true if exists
     */
    boolean existsByUserId(UUID userId);

    /**
     * Finds verified captain by user ID.
     *
     * @param userId user ID
     * @return optional verified captain
     */
    Optional<CaptainVerification> findByUserIdAndIsVerifiedTrue(UUID userId);
}

package com.openride.auth.repository;

import com.openride.auth.entity.OtpRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for OTP request operations.
 */
@Repository
public interface OtpRequestRepository extends JpaRepository<OtpRequest, UUID> {

    /**
     * Finds the most recent non-verified OTP request for a phone number.
     *
     * @param phoneNumber phone number to search for
     * @return optional OTP request
     */
    @Query("SELECT o FROM OtpRequest o WHERE o.phoneNumber = :phoneNumber " +
           "AND o.verified = false ORDER BY o.createdAt DESC LIMIT 1")
    Optional<OtpRequest> findLatestByPhoneNumber(@Param("phoneNumber") String phoneNumber);

    /**
     * Finds a valid (non-expired, non-verified) OTP request for a phone number.
     *
     * @param phoneNumber phone number to search for
     * @param now current timestamp
     * @return optional OTP request
     */
    @Query("SELECT o FROM OtpRequest o WHERE o.phoneNumber = :phoneNumber " +
           "AND o.verified = false AND o.expiresAt > :now ORDER BY o.createdAt DESC LIMIT 1")
    Optional<OtpRequest> findValidOtpByPhoneNumber(
        @Param("phoneNumber") String phoneNumber,
        @Param("now") LocalDateTime now
    );

    /**
     * Deletes expired OTP requests.
     *
     * @param now current timestamp
     * @return number of deleted records
     */
    @Modifying
    @Query("DELETE FROM OtpRequest o WHERE o.expiresAt < :now")
    int deleteExpiredOtps(@Param("now") LocalDateTime now);
}

package com.openride.payouts.repository;

import com.openride.payouts.model.entity.PayoutRequest;
import com.openride.payouts.model.enums.PayoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PayoutRequest entity operations.
 */
@Repository
public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, UUID> {

    /**
     * Find payout requests by driver ID.
     * 
     * @param driverId Driver ID
     * @param pageable Pagination parameters
     * @return Page of payout requests
     */
    Page<PayoutRequest> findByDriverIdOrderByRequestedAtDesc(UUID driverId, Pageable pageable);

    /**
     * Find payout requests by status.
     * 
     * @param status Payout status
     * @param pageable Pagination parameters
     * @return Page of payout requests
     */
    Page<PayoutRequest> findByStatusOrderByRequestedAtDesc(PayoutStatus status, Pageable pageable);

    /**
     * Find payout requests by driver and status.
     * 
     * @param driverId Driver ID
     * @param status Payout status
     * @param pageable Pagination parameters
     * @return Page of payout requests
     */
    Page<PayoutRequest> findByDriverIdAndStatusOrderByRequestedAtDesc(
            UUID driverId, PayoutStatus status, Pageable pageable);

    /**
     * Find pending payout for driver.
     * 
     * @param driverId Driver ID
     * @return Optional payout request
     */
    Optional<PayoutRequest> findByDriverIdAndStatus(UUID driverId, PayoutStatus status);

    /**
     * Check if driver has pending payout.
     * 
     * @param driverId Driver ID
     * @return true if has pending payout
     */
    @Query("SELECT COUNT(p) > 0 FROM PayoutRequest p " +
           "WHERE p.driverId = :driverId AND p.status = 'PENDING'")
    boolean hasPendingPayout(@Param("driverId") UUID driverId);

    /**
     * Find approved payouts without settlement ID.
     * These are ready for settlement processing.
     * 
     * @return List of approved payouts
     */
    @Query("SELECT p FROM PayoutRequest p " +
           "WHERE p.status = 'APPROVED' AND p.settlementId IS NULL " +
           "ORDER BY p.requestedAt ASC")
    List<PayoutRequest> findApprovedPayoutsForSettlement();

    /**
     * Find payouts by settlement ID.
     * 
     * @param settlementId Settlement ID
     * @return List of payout requests
     */
    List<PayoutRequest> findBySettlementId(UUID settlementId);

    /**
     * Count payouts by status.
     * 
     * @param status Payout status
     * @return Count of payouts
     */
    long countByStatus(PayoutStatus status);

    /**
     * Calculate total amount of pending payouts.
     * 
     * @return Total pending amount
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PayoutRequest p WHERE p.status = 'PENDING'")
    BigDecimal sumPendingPayouts();

    /**
     * Find expired pending payouts (older than specified days).
     * 
     * @param expiryDate Date before which requests are considered expired
     * @return List of expired payout requests
     */
    @Query("SELECT p FROM PayoutRequest p " +
           "WHERE p.status = 'PENDING' AND p.requestedAt < :expiryDate")
    List<PayoutRequest> findExpiredPendingPayouts(@Param("expiryDate") LocalDateTime expiryDate);
}

package com.openride.payouts.repository;

import com.openride.payouts.model.entity.EarningsLedger;
import com.openride.payouts.model.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for EarningsLedger entity operations.
 */
@Repository
public interface EarningsLedgerRepository extends JpaRepository<EarningsLedger, UUID> {

    /**
     * Find ledger entries by driver ID, ordered by created date descending.
     * 
     * @param driverId Driver ID
     * @param pageable Pagination parameters
     * @return Page of ledger entries
     */
    Page<EarningsLedger> findByDriverIdOrderByCreatedAtDesc(UUID driverId, Pageable pageable);

    /**
     * Find ledger entries by driver ID and transaction type.
     * 
     * @param driverId Driver ID
     * @param transactionType Transaction type
     * @param pageable Pagination parameters
     * @return Page of ledger entries
     */
    Page<EarningsLedger> findByDriverIdAndTransactionTypeOrderByCreatedAtDesc(
            UUID driverId, TransactionType transactionType, Pageable pageable);

    /**
     * Find ledger entries by driver ID within date range.
     * 
     * @param driverId Driver ID
     * @param startDate Start date
     * @param endDate End date
     * @param pageable Pagination parameters
     * @return Page of ledger entries
     */
    Page<EarningsLedger> findByDriverIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID driverId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Find ledger entries by reference.
     * 
     * @param referenceId Reference ID
     * @param referenceType Reference type
     * @return List of ledger entries
     */
    List<EarningsLedger> findByReferenceIdAndReferenceType(UUID referenceId, String referenceType);

    /**
     * Calculate total earnings for driver.
     * 
     * @param driverId Driver ID
     * @param transactionType Transaction type
     * @return Total amount
     */
    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM EarningsLedger l " +
           "WHERE l.driverId = :driverId AND l.transactionType = :transactionType")
    BigDecimal sumByDriverIdAndTransactionType(
            @Param("driverId") UUID driverId,
            @Param("transactionType") TransactionType transactionType);

    /**
     * Get earnings summary for driver within date range.
     * 
     * @param driverId Driver ID
     * @param startDate Start date
     * @param endDate End date
     * @return Total earnings
     */
    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM EarningsLedger l " +
           "WHERE l.driverId = :driverId " +
           "AND l.transactionType = 'EARNING' " +
           "AND l.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal calculateEarningsInPeriod(
            @Param("driverId") UUID driverId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}

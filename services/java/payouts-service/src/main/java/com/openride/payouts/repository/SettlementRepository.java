package com.openride.payouts.repository;

import com.openride.payouts.model.entity.Settlement;
import com.openride.payouts.model.enums.SettlementStatus;
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
 * Repository for Settlement entity operations.
 */
@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    /**
     * Find settlement by batch reference.
     * 
     * @param batchReference Batch reference
     * @return Optional settlement
     */
    Optional<Settlement> findByBatchReference(String batchReference);

    /**
     * Find settlements by status.
     * 
     * @param status Settlement status
     * @param pageable Pagination parameters
     * @return Page of settlements
     */
    Page<Settlement> findByStatusOrderByInitiatedAtDesc(SettlementStatus status, Pageable pageable);

    /**
     * Find all settlements ordered by initiated date.
     * 
     * @param pageable Pagination parameters
     * @return Page of settlements
     */
    Page<Settlement> findAllByOrderByInitiatedAtDesc(Pageable pageable);

    /**
     * Find settlements within date range.
     * 
     * @param startDate Start date
     * @param endDate End date
     * @param pageable Pagination parameters
     * @return Page of settlements
     */
    Page<Settlement> findByInitiatedAtBetweenOrderByInitiatedAtDesc(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Find settlements initiated by admin.
     * 
     * @param initiatedBy Admin user ID
     * @param pageable Pagination parameters
     * @return Page of settlements
     */
    Page<Settlement> findByInitiatedByOrderByInitiatedAtDesc(UUID initiatedBy, Pageable pageable);

    /**
     * Count settlements by status.
     * 
     * @param status Settlement status
     * @return Count of settlements
     */
    long countByStatus(SettlementStatus status);

    /**
     * Calculate total amount processed in completed settlements.
     * 
     * @return Total processed amount
     */
    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Settlement s " +
           "WHERE s.status = 'COMPLETED'")
    BigDecimal sumCompletedSettlements();

    /**
     * Calculate total amount in settlements within date range.
     * 
     * @param startDate Start date
     * @param endDate End date
     * @param status Settlement status
     * @return Total amount
     */
    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Settlement s " +
           "WHERE s.status = :status " +
           "AND s.initiatedAt BETWEEN :startDate AND :endDate")
    BigDecimal sumSettlementsInPeriod(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("status") SettlementStatus status);

    /**
     * Find processing settlements (potential stuck jobs).
     * 
     * @param processingThreshold Time threshold for processing status
     * @return List of settlements
     */
    @Query("SELECT s FROM Settlement s " +
           "WHERE s.status = 'PROCESSING' " +
           "AND s.initiatedAt < :processingThreshold")
    List<Settlement> findStuckProcessingSettlements(
            @Param("processingThreshold") LocalDateTime processingThreshold);
}

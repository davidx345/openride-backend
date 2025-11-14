package com.openride.admin.repository;

import com.openride.admin.model.Dispute;
import com.openride.admin.model.Dispute.DisputeStatus;
import com.openride.admin.model.Dispute.DisputeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Dispute entity.
 */
@Repository
public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    /**
     * Find disputes by status.
     */
    Page<Dispute> findByStatusOrderByCreatedAtDesc(DisputeStatus status, Pageable pageable);

    /**
     * Find disputes by booking ID.
     */
    List<Dispute> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /**
     * Find disputes by reporter ID.
     */
    Page<Dispute> findByReporterIdOrderByCreatedAtDesc(UUID reporterId, Pageable pageable);

    /**
     * Find disputes by type and status.
     */
    Page<Dispute> findByDisputeTypeAndStatusOrderByCreatedAtDesc(
        DisputeType disputeType, 
        DisputeStatus status, 
        Pageable pageable
    );

    /**
     * Find open disputes (OPEN or IN_PROGRESS).
     */
    @Query("SELECT d FROM Dispute d WHERE d.status IN ('OPEN', 'IN_PROGRESS') ORDER BY d.createdAt DESC")
    Page<Dispute> findOpenDisputes(Pageable pageable);

    /**
     * Count disputes by status.
     */
    long countByStatus(DisputeStatus status);

    /**
     * Find unresolved disputes older than a specific date.
     */
    @Query("SELECT d FROM Dispute d WHERE d.status IN ('OPEN', 'IN_PROGRESS') " +
           "AND d.createdAt < :before ORDER BY d.createdAt ASC")
    List<Dispute> findUnresolvedDisputesOlderThan(@Param("before") Instant before);
}

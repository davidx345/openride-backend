package com.openride.payouts.repository;

import com.openride.payouts.model.entity.PayoutAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for PayoutAuditLog entity operations.
 */
@Repository
public interface PayoutAuditLogRepository extends JpaRepository<PayoutAuditLog, UUID> {

    /**
     * Find audit logs by entity type and entity ID.
     * 
     * @param entityType Entity type
     * @param entityId Entity ID
     * @param pageable Pagination parameters
     * @return Page of audit logs
     */
    Page<PayoutAuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, UUID entityId, Pageable pageable);

    /**
     * Find audit logs by entity type.
     * 
     * @param entityType Entity type
     * @param pageable Pagination parameters
     * @return Page of audit logs
     */
    Page<PayoutAuditLog> findByEntityTypeOrderByCreatedAtDesc(
            String entityType, Pageable pageable);

    /**
     * Find audit logs by user.
     * 
     * @param performedBy User ID
     * @param pageable Pagination parameters
     * @return Page of audit logs
     */
    Page<PayoutAuditLog> findByPerformedByOrderByCreatedAtDesc(
            UUID performedBy, Pageable pageable);

    /**
     * Find audit logs within date range.
     * 
     * @param startDate Start date
     * @param endDate End date
     * @param pageable Pagination parameters
     * @return Page of audit logs
     */
    Page<PayoutAuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * Find audit logs by action.
     * 
     * @param action Action performed
     * @param pageable Pagination parameters
     * @return Page of audit logs
     */
    Page<PayoutAuditLog> findByActionOrderByCreatedAtDesc(
            String action, Pageable pageable);

    /**
     * Find recent audit logs for an entity.
     * 
     * @param entityType Entity type
     * @param entityId Entity ID
     * @param limit Maximum number of logs
     * @return List of audit logs
     */
    List<PayoutAuditLog> findTop10ByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, UUID entityId);
}

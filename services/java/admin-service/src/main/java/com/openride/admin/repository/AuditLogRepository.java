package com.openride.admin.repository;

import com.openride.admin.model.AuditLog;
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
 * Repository for AuditLog entity.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Find audit logs by entity.
     */
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
        String entityType,
        String entityId,
        Pageable pageable
    );

    /**
     * Find audit logs by actor.
     */
    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    /**
     * Find audit logs by action.
     */
    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    /**
     * Find audit logs by entity type.
     */
    Page<AuditLog> findByEntityTypeOrderByCreatedAtDesc(String entityType, Pageable pageable);

    /**
     * Find audit logs by service.
     */
    Page<AuditLog> findByServiceNameOrderByCreatedAtDesc(String serviceName, Pageable pageable);

    /**
     * Find audit logs within date range.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.createdAt >= :from AND a.createdAt <= :to " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLog> findByDateRange(
        @Param("from") Instant from,
        @Param("to") Instant to,
        Pageable pageable
    );

    /**
     * Find audit logs by actor role.
     */
    Page<AuditLog> findByActorRoleOrderByCreatedAtDesc(String actorRole, Pageable pageable);

    /**
     * Delete old audit logs (for retention policy).
     */
    void deleteByCreatedAtBefore(Instant before);

    /**
     * Count audit logs for a time period.
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.createdAt >= :from AND a.createdAt <= :to")
    long countByDateRange(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Find recent admin actions.
     */
    @Query("SELECT a FROM AuditLog a WHERE a.actorRole = 'ADMIN' " +
           "AND a.createdAt >= :since ORDER BY a.createdAt DESC")
    List<AuditLog> findRecentAdminActions(@Param("since") Instant since, Pageable pageable);
}

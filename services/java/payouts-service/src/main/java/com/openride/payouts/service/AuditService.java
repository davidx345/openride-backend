package com.openride.payouts.service;

import com.openride.payouts.model.PayoutAuditLog;
import com.openride.payouts.repository.PayoutAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Service for audit logging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final PayoutAuditLogRepository auditLogRepository;

    /**
     * Log an audit entry.
     * Uses REQUIRES_NEW to ensure audit log is saved even if parent transaction fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAuditEntry(
            String entityType,
            UUID entityId,
            String action,
            UUID performedBy,
            Map<String, Object> oldValues,
            Map<String, Object> newValues
    ) {
        try {
            PayoutAuditLog auditLog = new PayoutAuditLog();
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);
            auditLog.setAction(action);
            auditLog.setPerformedBy(performedBy);
            auditLog.setPerformedAt(LocalDateTime.now());
            auditLog.setOldValues(oldValues);
            auditLog.setNewValues(newValues);

            auditLogRepository.save(auditLog);
            
            log.debug("Audit log created: entity={}, action={}, user={}", 
                    entityType, action, performedBy);
        } catch (Exception e) {
            // Don't fail parent transaction if audit logging fails
            log.error("Failed to create audit log: entity={}, action={}", entityType, action, e);
        }
    }

    /**
     * Get audit logs for an entity.
     */
    @Transactional(readOnly = true)
    public Page<PayoutAuditLog> getAuditLogs(
            String entityType,
            UUID entityId,
            Pageable pageable
    ) {
        log.debug("Getting audit logs for entity: type={}, id={}", entityType, entityId);
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
    }

    /**
     * Get audit logs by user.
     */
    @Transactional(readOnly = true)
    public Page<PayoutAuditLog> getAuditLogsByUser(UUID userId, Pageable pageable) {
        log.debug("Getting audit logs for user: {}", userId);
        return auditLogRepository.findByPerformedBy(userId, pageable);
    }

    /**
     * Get audit logs by date range.
     */
    @Transactional(readOnly = true)
    public Page<PayoutAuditLog> getAuditLogsByDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable
    ) {
        log.debug("Getting audit logs for date range: {} to {}", startDate, endDate);
        return auditLogRepository.findByPerformedAtBetween(startDate, endDate, pageable);
    }
}

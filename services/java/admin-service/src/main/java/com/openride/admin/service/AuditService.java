package com.openride.admin.service;

import com.openride.admin.dto.AuditLogResponse;
import com.openride.admin.model.AuditLog;
import com.openride.admin.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Service for audit logging and viewing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Log an action to the audit trail.
     */
    @Transactional
    public void logAction(String entityType, String entityId, String action, UUID actorId, String changes) {
        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .actorId(actorId)
                .actorType("USER")
                .actorRole("ADMIN")  // Assuming all actions in admin service are by admins
                .changes(changes)
                .serviceName("admin-service")
                .build();

        auditLogRepository.save(auditLog);
    }

    /**
     * Log an action with full context.
     */
    @Transactional
    public void logActionWithContext(
            String entityType,
            String entityId,
            String action,
            UUID actorId,
            String changes,
            String ipAddress,
            String userAgent,
            String requestId,
            String endpoint,
            String httpMethod
    ) {
        AuditLog auditLog = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .actorId(actorId)
                .actorType("USER")
                .actorRole("ADMIN")
                .changes(changes)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .requestId(requestId)
                .serviceName("admin-service")
                .endpoint(endpoint)
                .httpMethod(httpMethod)
                .build();

        auditLogRepository.save(auditLog);
    }

    /**
     * Get audit logs for a specific entity.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogsByEntity(
            String entityType,
            String entityId,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                entityType,
                entityId,
                pageable
        );

        return logs.map(AuditLogResponse::fromEntity);
    }

    /**
     * Get audit logs by actor.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogsByActor(UUID actorId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> logs = auditLogRepository.findByActorIdOrderByCreatedAtDesc(actorId, pageable);

        return logs.map(AuditLogResponse::fromEntity);
    }

    /**
     * Get audit logs by action type.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogsByAction(String action, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> logs = auditLogRepository.findByActionOrderByCreatedAtDesc(action, pageable);

        return logs.map(AuditLogResponse::fromEntity);
    }

    /**
     * Get audit logs within a date range.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogsByDateRange(
            Instant from,
            Instant to,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> logs = auditLogRepository.findByDateRange(from, to, pageable);

        return logs.map(AuditLogResponse::fromEntity);
    }

    /**
     * Get recent admin actions.
     */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> getRecentAdminActions(int hours, int limit) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        Pageable pageable = PageRequest.of(0, limit);
        List<AuditLog> logs = auditLogRepository.findRecentAdminActions(since, pageable);

        return logs.stream()
                .map(AuditLogResponse::fromEntity)
                .toList();
    }

    /**
     * Scheduled job to clean up old audit logs.
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupOldAuditLogs() {
        log.info("Running scheduled audit log cleanup");

        // Keep logs for 365 days
        Instant cutoffDate = Instant.now().minus(365, ChronoUnit.DAYS);

        try {
            auditLogRepository.deleteByCreatedAtBefore(cutoffDate);
            log.info("Completed audit log cleanup for records older than {}", cutoffDate);
        } catch (Exception e) {
            log.error("Error during audit log cleanup", e);
        }
    }
}

package com.openride.booking.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Audit logging service for sensitive operations
 * 
 * Logs:
 * - Booking creation
 * - Booking cancellation
 * - Payment confirmation
 * - Unauthorized access attempts
 */
@Slf4j
@Service
public class AuditLogger {

    /**
     * Log booking creation
     */
    public void logBookingCreated(String userId, String bookingId, String ipAddress, Map<String, Object> details) {
        AuditLog auditLog = AuditLog.builder()
            .eventType("BOOKING_CREATED")
            .userId(userId)
            .action("CREATE")
            .entityType("BOOKING")
            .entityId(bookingId)
            .ipAddress(ipAddress)
            .details(details)
            .timestamp(Instant.now())
            .outcome("SUCCESS")
            .build();

        logAuditEntry(auditLog);
    }

    /**
     * Log booking cancellation
     */
    public void logBookingCancelled(String userId, String bookingId, String ipAddress, String reason) {
        AuditLog auditLog = AuditLog.builder()
            .eventType("BOOKING_CANCELLED")
            .userId(userId)
            .action("CANCEL")
            .entityType("BOOKING")
            .entityId(bookingId)
            .ipAddress(ipAddress)
            .details(Map.of("reason", reason))
            .timestamp(Instant.now())
            .outcome("SUCCESS")
            .build();

        logAuditEntry(auditLog);
    }

    /**
     * Log payment confirmation
     */
    public void logPaymentConfirmed(String userId, String bookingId, String paymentId, String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
            .eventType("PAYMENT_CONFIRMED")
            .userId(userId)
            .action("CONFIRM")
            .entityType("BOOKING")
            .entityId(bookingId)
            .ipAddress(ipAddress)
            .details(Map.of("paymentId", paymentId))
            .timestamp(Instant.now())
            .outcome("SUCCESS")
            .build();

        logAuditEntry(auditLog);
    }

    /**
     * Log unauthorized access attempt
     */
    public void logUnauthorizedAccess(String userId, String action, String entityId, String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
            .eventType("UNAUTHORIZED_ACCESS")
            .userId(userId)
            .action(action)
            .entityType("BOOKING")
            .entityId(entityId)
            .ipAddress(ipAddress)
            .timestamp(Instant.now())
            .outcome("UNAUTHORIZED")
            .build();

        logAuditEntry(auditLog);
    }

    /**
     * Log audit entry to structured logging system
     * 
     * In production, this should write to a dedicated audit log system
     * (e.g., separate log file, database, SIEM)
     */
    private void logAuditEntry(AuditLog auditLog) {
        log.info("AUDIT: {} | User: {} | Action: {} | Entity: {} ({}) | Outcome: {} | IP: {} | Timestamp: {}",
            auditLog.getEventType(),
            auditLog.getUserId(),
            auditLog.getAction(),
            auditLog.getEntityType(),
            auditLog.getEntityId(),
            auditLog.getOutcome(),
            auditLog.getIpAddress(),
            auditLog.getTimestamp()
        );

        // In production: Write to audit database or SIEM
        // auditLogRepository.save(auditLog);
    }
}

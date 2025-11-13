package com.openride.booking.audit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Audit log entry for sensitive operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    private String eventType;
    private String userId;
    private String action;
    private String entityType;
    private String entityId;
    private String ipAddress;
    private String userAgent;
    private Map<String, Object> details;
    private Instant timestamp;
    private String outcome; // SUCCESS, FAILURE, UNAUTHORIZED
}

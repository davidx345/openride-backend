package com.openride.admin.dto;

import com.openride.admin.model.AuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for AuditLog response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private UUID id;
    private String entityType;
    private String entityId;
    private String action;
    private UUID actorId;
    private String actorType;
    private String actorRole;
    private String changes;
    private String ipAddress;
    private String userAgent;
    private String requestId;
    private String serviceName;
    private String endpoint;
    private String httpMethod;
    private Integer statusCode;
    private String errorMessage;
    private Instant createdAt;

    public static AuditLogResponse fromEntity(AuditLog auditLog) {
        return AuditLogResponse.builder()
                .id(auditLog.getId())
                .entityType(auditLog.getEntityType())
                .entityId(auditLog.getEntityId())
                .action(auditLog.getAction())
                .actorId(auditLog.getActorId())
                .actorType(auditLog.getActorType())
                .actorRole(auditLog.getActorRole())
                .changes(auditLog.getChanges())
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .requestId(auditLog.getRequestId())
                .serviceName(auditLog.getServiceName())
                .endpoint(auditLog.getEndpoint())
                .httpMethod(auditLog.getHttpMethod())
                .statusCode(auditLog.getStatusCode())
                .errorMessage(auditLog.getErrorMessage())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}

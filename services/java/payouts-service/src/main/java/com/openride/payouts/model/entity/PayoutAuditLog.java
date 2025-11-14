package com.openride.payouts.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Audit log entity for tracking all payout-related actions.
 * Immutable once created.
 */
@Entity
@Table(name = "payout_audit_logs", indexes = {
    @Index(name = "idx_audit_logs_entity", columnList = "entity_type,entity_id,created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "performed_by")
    private UUID performedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_values", columnDefinition = "jsonb")
    private Map<String, Object> oldValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_values", columnDefinition = "jsonb")
    private Map<String, Object> newValues;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Create audit log for entity creation.
     * 
     * @param entityType Entity type (e.g., "PAYOUT_REQUEST")
     * @param entityId Entity ID
     * @param performedBy User who performed the action
     * @param newValues New entity values
     * @return Audit log
     */
    public static PayoutAuditLog createLog(
            String entityType,
            UUID entityId,
            String action,
            UUID performedBy,
            Map<String, Object> newValues) {
        return PayoutAuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .performedBy(performedBy)
                .newValues(newValues)
                .build();
    }

    /**
     * Create audit log for entity update.
     * 
     * @param entityType Entity type
     * @param entityId Entity ID
     * @param action Action performed
     * @param performedBy User who performed the action
     * @param oldValues Old values
     * @param newValues New values
     * @return Audit log
     */
    public static PayoutAuditLog updateLog(
            String entityType,
            UUID entityId,
            String action,
            UUID performedBy,
            Map<String, Object> oldValues,
            Map<String, Object> newValues) {
        return PayoutAuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .performedBy(performedBy)
                .oldValues(oldValues)
                .newValues(newValues)
                .build();
    }
}

package com.openride.admin.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * User suspension entity for ban/suspension management.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_suspensions", indexes = {
    @Index(name = "idx_user_suspensions_user", columnList = "user_id"),
    @Index(name = "idx_user_suspensions_active", columnList = "is_active"),
    @Index(name = "idx_user_suspensions_type", columnList = "suspension_type"),
    @Index(name = "idx_user_suspensions_start_date", columnList = "start_date")
})
@EntityListeners(AuditingEntityListener.class)
public class UserSuspension {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "suspension_type", nullable = false, length = 20)
    private SuspensionType suspensionType;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "start_date", nullable = false)
    @Builder.Default
    private Instant startDate = Instant.now();

    @Column(name = "end_date")
    private Instant endDate;

    @Column(name = "suspended_by", nullable = false)
    private UUID suspendedBy;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum SuspensionType {
        TEMPORARY,
        PERMANENT
    }

    /**
     * Check if this suspension is currently in effect.
     */
    public boolean isCurrentlyActive() {
        if (!isActive) {
            return false;
        }
        
        Instant now = Instant.now();
        
        if (startDate.isAfter(now)) {
            return false;
        }
        
        if (suspensionType == SuspensionType.PERMANENT) {
            return true;
        }
        
        return endDate == null || endDate.isAfter(now);
    }
}

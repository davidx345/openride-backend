package com.openride.ticketing.model.entity;

import com.openride.ticketing.model.enums.VerificationMethod;
import com.openride.ticketing.model.enums.VerificationResult;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ticket verification log entity for audit trail of all verification attempts.
 * 
 * Records:
 * - Who verified the ticket
 * - When verification occurred
 * - What method was used
 * - The result of verification
 * - Client information (IP, user agent)
 * - Error details if verification failed
 */
@Entity
@Table(name = "ticket_verification_logs", indexes = {
    @Index(name = "idx_verification_logs_ticket_id", columnList = "ticket_id"),
    @Index(name = "idx_verification_logs_verifier_id", columnList = "verifier_id"),
    @Index(name = "idx_verification_logs_result", columnList = "result"),
    @Index(name = "idx_verification_logs_time", columnList = "verification_time"),
    @Index(name = "idx_verification_logs_method", columnList = "verification_method")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketVerificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_verification_ticket"))
    private Ticket ticket;

    /**
     * ID of the verifier (driver or admin).
     * Null if verification was anonymous.
     */
    @Column(name = "verifier_id")
    private UUID verifierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", nullable = false, length = 50)
    private VerificationMethod verificationMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private VerificationResult result;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "verification_time", nullable = false)
    @Builder.Default
    private LocalDateTime verificationTime = LocalDateTime.now();

    /**
     * Check if verification was successful.
     * 
     * @return true if result is VALID
     */
    public boolean isSuccessful() {
        return result == VerificationResult.VALID;
    }

    /**
     * Check if verification failed.
     * 
     * @return true if result is not VALID
     */
    public boolean isFailed() {
        return result != VerificationResult.VALID;
    }
}

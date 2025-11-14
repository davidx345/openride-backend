package com.openride.payouts.dto;

import com.openride.payouts.model.enums.PayoutStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for payout request response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutResponse {
    private UUID id;
    private UUID driverId;
    private BankAccountResponse bankAccount;
    private BigDecimal amount;
    private PayoutStatus status;
    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;
    private UUID reviewedBy;
    private String reviewerNotes;
    private UUID settlementId;
    private LocalDateTime completedAt;
    private String failureReason;
    private LocalDateTime createdAt;
}

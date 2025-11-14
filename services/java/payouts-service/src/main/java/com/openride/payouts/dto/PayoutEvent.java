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
 * Event published for payout state changes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutEvent {
    private UUID payoutId;
    private UUID driverId;
    private BigDecimal amount;
    private PayoutStatus status;
    private String eventType; // REQUESTED, APPROVED, REJECTED, COMPLETED, FAILED
    private LocalDateTime timestamp;
    private String notes;
}

package com.openride.payouts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for wallet response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {
    private UUID id;
    private UUID driverId;
    private BigDecimal availableBalance;
    private BigDecimal pendingPayout;
    private BigDecimal totalEarnings;
    private BigDecimal totalPaidOut;
    private LocalDateTime lastPayoutAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

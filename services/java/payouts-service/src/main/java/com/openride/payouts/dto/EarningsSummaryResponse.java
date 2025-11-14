package com.openride.payouts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for earnings summary response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarningsSummaryResponse {
    private BigDecimal availableBalance;
    private BigDecimal pendingPayout;
    private BigDecimal totalEarnings;
    private BigDecimal totalPaidOut;
    private BigDecimal lifetimeEarnings;
    private Integer totalTrips;
    private LocalDateTime lastPayoutAt;
    private LocalDateTime lastEarningAt;
}

package com.openride.payouts.dto;

import com.openride.payouts.model.enums.SettlementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for settlement response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementResponse {
    private UUID id;
    private String batchReference;
    private BigDecimal totalAmount;
    private Integer payoutCount;
    private SettlementStatus status;
    private UUID initiatedBy;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
    private String failureReason;
    private String provider;
    private String providerReference;
    private LocalDateTime createdAt;
}

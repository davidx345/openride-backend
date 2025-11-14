package com.openride.payouts.dto;

import com.openride.payouts.model.enums.LedgerEntryType;
import com.openride.payouts.model.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for ledger entry response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntryResponse {
    private UUID id;
    private UUID driverId;
    private LedgerEntryType entryType;
    private TransactionType transactionType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private UUID referenceId;
    private String referenceType;
    private String description;
    private LocalDateTime createdAt;
}

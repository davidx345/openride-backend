package com.openride.payouts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for bank account response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountResponse {
    private UUID id;
    private UUID driverId;
    private String accountNumber;
    private String maskedAccountNumber;
    private String accountName;
    private String bankCode;
    private String bankName;
    private Boolean isVerified;
    private Boolean isPrimary;
    private LocalDateTime verifiedAt;
    private LocalDateTime createdAt;
}

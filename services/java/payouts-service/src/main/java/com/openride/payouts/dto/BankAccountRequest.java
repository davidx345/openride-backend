package com.openride.payouts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating bank account.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankAccountRequest {

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^\\d{10}$", message = "Account number must be 10 digits")
    private String accountNumber;

    @NotBlank(message = "Bank code is required")
    @Pattern(regexp = "^\\d{3}$", message = "Bank code must be 3 digits")
    private String bankCode;

    private Boolean isPrimary = false;
}

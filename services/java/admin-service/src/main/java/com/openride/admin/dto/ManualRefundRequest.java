package com.openride.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for manual refund request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualRefundRequest {

    @NotNull(message = "Payment ID is required")
    private UUID paymentId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Reason is required")
    @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
    private String reason;

    @Size(max = 2000, message = "Admin notes must not exceed 2000 characters")
    private String adminNotes;
}

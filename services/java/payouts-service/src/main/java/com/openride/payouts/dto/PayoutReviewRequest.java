package com.openride.payouts.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for payout approval/rejection.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutReviewRequest {

    @NotBlank(message = "Notes are required")
    private String notes;
}

package com.openride.user.dto;

import com.openride.user.enums.KycStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating KYC status (admin only).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateKycStatusRequest {

    @NotNull(message = "KYC status is required")
    private KycStatus status;

    private String notes;
}

package com.openride.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for submitting KYC documents.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KycDocumentsRequest {

    @NotBlank(message = "BVN is required")
    private String bvn;

    @NotBlank(message = "License number is required")
    private String licenseNumber;

    @NotBlank(message = "License photo URL is required")
    private String licensePhotoUrl;

    private String vehiclePhotoUrl;
}

package com.openride.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for submitting Captain (driver's license) verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptainVerificationRequest {

    /**
     * Nigerian driver's license number.
     */
    @NotBlank(message = "Driver's license number is required")
    @Pattern(regexp = "^[A-Z0-9]{10,20}$", message = "Invalid driver's license format")
    private String licenseNumber;

    /**
     * URL to uploaded driver's license image.
     */
    @NotBlank(message = "Driver's license image URL is required")
    private String licensePhotoUrl;
}

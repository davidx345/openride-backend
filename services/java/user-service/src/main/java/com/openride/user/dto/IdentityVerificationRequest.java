package com.openride.user.dto;

import com.openride.user.enums.GovernmentIdType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for submitting identity verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityVerificationRequest {

    /**
     * Nigerian National Identification Number (11 digits).
     */
    @NotBlank(message = "NIN is required")
    @Size(min = 11, max = 11, message = "NIN must be exactly 11 digits")
    @Pattern(regexp = "^[0-9]{11}$", message = "NIN must contain only digits")
    private String nin;

    /**
     * Type of government-issued ID.
     */
    @NotNull(message = "Government ID type is required")
    private GovernmentIdType governmentIdType;

    /**
     * URL to uploaded government ID image.
     */
    @NotBlank(message = "Government ID image URL is required")
    private String governmentIdUrl;

    /**
     * URL to user's selfie image for face matching.
     */
    @NotBlank(message = "Selfie image URL is required")
    private String selfieUrl;
}

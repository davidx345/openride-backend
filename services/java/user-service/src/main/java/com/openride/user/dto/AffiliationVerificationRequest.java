package com.openride.user.dto;

import com.openride.user.enums.AffiliationType;
import com.openride.user.enums.VerificationMethod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for submitting affiliation verification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AffiliationVerificationRequest {

    /**
     * Type of affiliation: STUDENT or EMPLOYEE.
     */
    @NotNull(message = "Affiliation type is required")
    private AffiliationType affiliationType;

    /**
     * Name of organization (company or school).
     */
    @NotBlank(message = "Organization name is required")
    private String organizationName;

    /**
     * Method of verification: ID_CARD or EMAIL.
     */
    @NotNull(message = "Verification method is required")
    private VerificationMethod verificationMethod;

    /**
     * URL to uploaded work/student ID card image.
     * Required when verificationMethod is ID_CARD.
     */
    private String idCardUrl;

    /**
     * Work/school email address for verification.
     * Required when verificationMethod is EMAIL.
     */
    @Email(message = "Invalid email format")
    private String verificationEmail;
}

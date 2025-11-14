package com.openride.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for resolving a dispute.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveDisputeRequest {

    @NotBlank(message = "Status is required")
    private String status;  // RESOLVED or REJECTED

    @NotBlank(message = "Resolution notes are required")
    @Size(min = 10, max = 5000, message = "Resolution notes must be between 10 and 5000 characters")
    private String resolutionNotes;
}

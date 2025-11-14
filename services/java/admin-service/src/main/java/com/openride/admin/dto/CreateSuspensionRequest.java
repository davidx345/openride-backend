package com.openride.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for creating a user suspension.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSuspensionRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotBlank(message = "Suspension type is required")
    private String suspensionType;  // TEMPORARY or PERMANENT

    @NotBlank(message = "Reason is required")
    @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
    private String reason;

    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;

    private Instant endDate;  // Required for TEMPORARY, must be null for PERMANENT
}

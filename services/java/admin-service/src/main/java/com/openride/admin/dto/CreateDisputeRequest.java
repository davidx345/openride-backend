package com.openride.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO for creating a dispute.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDisputeRequest {

    @NotNull(message = "Booking ID is required")
    private UUID bookingId;

    @NotNull(message = "Reporter ID is required")
    private UUID reporterId;

    private UUID reportedId;

    @NotBlank(message = "Dispute type is required")
    private String disputeType;  // PAYMENT, BOOKING, DRIVER_BEHAVIOR, RIDER_BEHAVIOR, OTHER

    @NotBlank(message = "Subject is required")
    @Size(min = 5, max = 255, message = "Subject must be between 5 and 255 characters")
    private String subject;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 5000, message = "Description must be between 10 and 5000 characters")
    private String description;

    private String[] evidenceUrls;
}

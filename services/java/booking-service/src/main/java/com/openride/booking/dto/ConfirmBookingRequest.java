package com.openride.booking.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for confirming a booking (internal use)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmBookingRequest {

    @NotNull(message = "Payment ID is required")
    private UUID paymentId;

    @NotNull(message = "Payment status is required")
    private String paymentStatus;
}

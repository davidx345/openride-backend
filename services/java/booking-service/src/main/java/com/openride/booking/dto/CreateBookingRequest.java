package com.openride.booking.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for creating a booking
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBookingRequest {

    @NotNull(message = "Route ID is required")
    private UUID routeId;

    @NotNull(message = "Origin stop ID is required")
    private UUID originStopId;

    @NotNull(message = "Destination stop ID is required")
    private UUID destinationStopId;

    @NotNull(message = "Travel date is required")
    @Future(message = "Travel date must be in the future")
    private LocalDate travelDate;

    @NotNull(message = "Number of seats is required")
    @Min(value = 1, message = "Must book at least 1 seat")
    @Max(value = 10, message = "Cannot book more than 10 seats")
    private Integer seatsBooked;

    @Size(max = 100, message = "Idempotency key must be at most 100 characters")
    private String idempotencyKey;
}

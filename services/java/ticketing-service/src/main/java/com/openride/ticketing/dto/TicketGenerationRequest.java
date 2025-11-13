package com.openride.ticketing.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Request DTO for generating a ticket (internal service-to-service call).
 * Called by Booking Service after payment confirmation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketGenerationRequest {

    @NotNull(message = "Booking ID is required")
    private UUID bookingId;

    @NotNull(message = "Rider ID is required")
    private UUID riderId;

    @NotNull(message = "Driver ID is required")
    private UUID driverId;

    @NotNull(message = "Route ID is required")
    private UUID routeId;

    @NotNull(message = "Trip date is required")
    @Future(message = "Trip date must be in the future")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime tripDate;

    @NotNull(message = "Seat number is required")
    @Min(value = 1, message = "Seat number must be at least 1")
    private Integer seatNumber;

    @NotBlank(message = "Pickup stop is required")
    @Size(max = 255, message = "Pickup stop must not exceed 255 characters")
    private String pickupStop;

    @NotBlank(message = "Dropoff stop is required")
    @Size(max = 255, message = "Dropoff stop must not exceed 255 characters")
    private String dropoffStop;

    @NotNull(message = "Fare is required")
    @DecimalMin(value = "0.01", message = "Fare must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Invalid fare format")
    private BigDecimal fare;
}

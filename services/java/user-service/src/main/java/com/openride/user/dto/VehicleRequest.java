package com.openride.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for registering a vehicle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleRequest {

    /**
     * Vehicle manufacturer (e.g., Toyota, Honda).
     */
    @NotBlank(message = "Vehicle make is required")
    private String make;

    /**
     * Vehicle model (e.g., Camry, Civic).
     */
    @NotBlank(message = "Vehicle model is required")
    private String model;

    /**
     * Manufacturing year.
     */
    @NotNull(message = "Vehicle year is required")
    @Min(value = 2000, message = "Vehicle must be year 2000 or newer")
    @Max(value = 2030, message = "Invalid year")
    private Integer year;

    /**
     * Vehicle color.
     */
    @NotBlank(message = "Vehicle color is required")
    private String color;

    /**
     * License plate number.
     */
    @NotBlank(message = "License plate is required")
    @Pattern(regexp = "^[A-Z0-9\\-]{5,15}$", message = "Invalid license plate format")
    private String licensePlate;

    /**
     * Number of available passenger seats.
     */
    @NotNull(message = "Number of seats is required")
    @Min(value = 1, message = "Must have at least 1 seat")
    @Max(value = 12, message = "Maximum 12 seats allowed")
    private Integer seatsAvailable;

    /**
     * URL to vehicle registration document.
     */
    private String registrationUrl;

    /**
     * URL to vehicle insurance document.
     */
    private String insuranceUrl;

    /**
     * URL to vehicle front photo.
     */
    private String photoFrontUrl;

    /**
     * URL to vehicle back photo.
     */
    private String photoBackUrl;

    /**
     * URL to vehicle interior photo.
     */
    private String photoInteriorUrl;
}

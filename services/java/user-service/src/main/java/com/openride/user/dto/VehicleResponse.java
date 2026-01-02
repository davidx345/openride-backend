package com.openride.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for vehicle information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleResponse {

    private UUID id;
    private String make;
    private String model;
    private Integer year;
    private String color;
    private String licensePlate;
    private Integer seatsAvailable;
    private String registrationUrl;
    private String insuranceUrl;
    private String photoFrontUrl;
    private String photoBackUrl;
    private String photoInteriorUrl;
    private Boolean isVerified;
    private Boolean isActive;
    private String displayName;
    private LocalDateTime createdAt;
}

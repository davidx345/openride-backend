package com.openride.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for route information from Driver Service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteDTO {

    private UUID id;
    private String name;
    private UUID driverId;
    private Integer seatsAvailable;
    private String departureTime;
    private String status;
}

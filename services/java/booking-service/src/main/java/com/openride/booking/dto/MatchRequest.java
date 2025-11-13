package com.openride.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request DTO for matchmaking service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchRequest {
    
    private UUID routeId;
    private String originStopId;
    private String destinationStopId;
    private LocalDate travelDate;
    private Integer seatsRequired;
}

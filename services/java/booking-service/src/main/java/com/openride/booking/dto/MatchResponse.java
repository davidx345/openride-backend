package com.openride.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response DTO from matchmaking service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResponse {
    
    private UUID routeId;
    private UUID driverId;
    private BigDecimal baseFare;
    private BigDecimal pricePerSeat;
    private BigDecimal totalPrice;
    private Integer availableSeats;
    private Boolean isAvailable;
    private String departureTime;
    private String arrivalTime;
}

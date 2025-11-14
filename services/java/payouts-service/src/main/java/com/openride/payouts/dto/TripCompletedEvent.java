package com.openride.payouts.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event received when trip is completed (from Kafka).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripCompletedEvent {
    private UUID tripId;
    private UUID driverId;
    private UUID riderId;
    private UUID routeId;
    private BigDecimal totalPrice;
    private BigDecimal basePrice;
    private Integer seatsBooked;
    private LocalDateTime completedAt;
    private LocalDateTime tripStartedAt;
    private String status;
}

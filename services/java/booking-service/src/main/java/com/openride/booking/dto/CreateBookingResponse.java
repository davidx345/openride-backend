package com.openride.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for booking creation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBookingResponse {

    private BookingDTO booking;
    private String paymentUrl;
    private Instant expiresAt;
    private String message;
}

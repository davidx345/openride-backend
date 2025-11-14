package com.openride.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for admin booking search filters.
 * Supports multiple filter criteria for comprehensive booking search.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBookingFilters {

    private UUID riderId;
    
    private UUID driverId;
    
    private UUID routeId;
    
    private String status; // PENDING, CONFIRMED, CANCELLED, COMPLETED, etc.
    
    private String paymentStatus; // PENDING, PAID, FAILED, REFUNDED
    
    private LocalDate travelDateFrom;
    
    private LocalDate travelDateTo;
    
    private String bookingReference;
    
    private LocalDate createdDateFrom;
    
    private LocalDate createdDateTo;
    
    private Integer page;
    
    private Integer size;
    
    private String sortBy; // createdAt, travelDate, totalPrice
    
    private String sortDirection; // ASC, DESC
}

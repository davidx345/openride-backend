package com.openride.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for paginated booking search results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSearchResponse {

    private List<BookingResponse> bookings;
    
    private long totalElements;
    
    private int totalPages;
    
    private int currentPage;
    
    private int pageSize;
    
    private boolean hasNext;
    
    private boolean hasPrevious;
}

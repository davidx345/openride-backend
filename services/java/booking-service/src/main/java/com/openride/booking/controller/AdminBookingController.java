package com.openride.booking.controller;

import com.openride.booking.dto.AdminBookingFilters;
import com.openride.booking.dto.BookingResponse;
import com.openride.booking.dto.BookingSearchResponse;
import com.openride.booking.service.AdminBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Admin controller for booking management and search.
 * Provides comprehensive booking search, filtering, and management capabilities.
 */
@Slf4j
@RestController
@RequestMapping("/v1/admin/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Bookings", description = "Admin endpoints for booking management and search")
@SecurityRequirement(name = "bearerAuth")
public class AdminBookingController {

    private final AdminBookingService adminBookingService;

    /**
     * Search bookings with advanced filtering.
     *
     * @param riderId optional rider ID filter
     * @param driverId optional driver ID filter
     * @param routeId optional route ID filter
     * @param status optional booking status filter
     * @param paymentStatus optional payment status filter
     * @param travelDateFrom optional travel date from
     * @param travelDateTo optional travel date to
     * @param bookingReference optional booking reference
     * @param createdDateFrom optional created date from
     * @param createdDateTo optional created date to
     * @param page page number (default 0)
     * @param size page size (default 20, max 100)
     * @param sortBy sort field (default createdAt)
     * @param sortDirection sort direction (default DESC)
     * @return paginated booking search results
     */
    @GetMapping
    @Operation(
        summary = "Search bookings with filters",
        description = "Advanced booking search with multiple filter criteria (Admin only)"
    )
    public ResponseEntity<BookingSearchResponse> searchBookings(
            @RequestParam(required = false) UUID riderId,
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) UUID routeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) LocalDate travelDateFrom,
            @RequestParam(required = false) LocalDate travelDateTo,
            @RequestParam(required = false) String bookingReference,
            @RequestParam(required = false) LocalDate createdDateFrom,
            @RequestParam(required = false) LocalDate createdDateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        log.info("Admin: Searching bookings with filters - riderId={}, driverId={}, status={}, page={}", 
                riderId, driverId, status, page);

        // Validate page size
        if (size > 100) {
            size = 100;
        }

        AdminBookingFilters filters = AdminBookingFilters.builder()
                .riderId(riderId)
                .driverId(driverId)
                .routeId(routeId)
                .status(status)
                .paymentStatus(paymentStatus)
                .travelDateFrom(travelDateFrom)
                .travelDateTo(travelDateTo)
                .bookingReference(bookingReference)
                .createdDateFrom(createdDateFrom)
                .createdDateTo(createdDateTo)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .build();

        BookingSearchResponse response = adminBookingService.searchBookings(filters);

        return ResponseEntity.ok(response);
    }

    /**
     * Get booking by ID with full details.
     *
     * @param bookingId booking ID
     * @return booking details
     */
    @GetMapping("/{bookingId}")
    @Operation(
        summary = "Get booking details",
        description = "Get full booking details by ID (Admin only)"
    )
    public ResponseEntity<BookingResponse> getBookingById(@PathVariable UUID bookingId) {
        log.info("Admin: Getting booking details for bookingId={}", bookingId);

        BookingResponse response = adminBookingService.getBookingById(bookingId);

        return ResponseEntity.ok(response);
    }

    /**
     * Get booking statistics.
     *
     * @param dateFrom optional start date
     * @param dateTo optional end date
     * @return booking statistics
     */
    @GetMapping("/statistics")
    @Operation(
        summary = "Get booking statistics",
        description = "Get aggregated booking statistics for a date range (Admin only)"
    )
    public ResponseEntity<Map<String, Object>> getBookingStatistics(
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo
    ) {
        log.info("Admin: Getting booking statistics from {} to {}", dateFrom, dateTo);

        LocalDate effectiveDateFrom = dateFrom != null ? dateFrom : LocalDate.now().minusDays(30);
        LocalDate effectiveDateTo = dateTo != null ? dateTo : LocalDate.now();

        Map<String, Object> statistics = adminBookingService.getBookingStatistics(
                effectiveDateFrom, 
                effectiveDateTo
        );

        return ResponseEntity.ok(statistics);
    }

    /**
     * Force cancel a booking (admin override).
     *
     * @param bookingId booking ID
     * @param reason cancellation reason
     * @return cancelled booking details
     */
    @PostMapping("/{bookingId}/force-cancel")
    @Operation(
        summary = "Force cancel booking",
        description = "Admin-initiated force cancellation with refund processing (Admin only)"
    )
    public ResponseEntity<BookingResponse> forceCancelBooking(
            @PathVariable UUID bookingId,
            @RequestParam String reason
    ) {
        log.info("Admin: Force cancelling booking {} with reason: {}", bookingId, reason);

        BookingResponse response = adminBookingService.forceCancelBooking(bookingId, reason);

        return ResponseEntity.ok(response);
    }
}

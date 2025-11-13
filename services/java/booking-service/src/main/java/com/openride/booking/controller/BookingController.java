package com.openride.booking.controller;

import com.openride.booking.dto.*;
import com.openride.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for booking operations
 * 
 * Endpoints:
 * - POST /v1/bookings - Create booking
 * - GET /v1/bookings/{id} - Get booking details
 * - GET /v1/bookings - List user bookings
 * - GET /v1/bookings/upcoming - Get upcoming bookings
 * - POST /v1/bookings/{id}/cancel - Cancel booking
 * - POST /v1/bookings/{id}/confirm - Confirm booking (internal)
 */
@Slf4j
@RestController
@RequestMapping("/v1/bookings")
@Tag(name = "Bookings", description = "Booking management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @Operation(
        summary = "Create a new booking",
        description = "Create a booking with seat hold and payment initiation. " +
                     "Seats are held for 10 minutes pending payment completion."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "201",
            description = "Booking created successfully",
            content = @Content(schema = @Schema(implementation = CreateBookingResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "409", description = "Insufficient seats available"),
        @ApiResponse(responseCode = "503", description = "Service temporarily unavailable")
    })
    public ResponseEntity<CreateBookingResponse> createBooking(
        @Valid @RequestBody CreateBookingRequest request
    ) {
        log.info("Creating booking for route {} with {} seats",
            request.getRouteId(), request.getSeatsBooked());

        CreateBookingResponse response = bookingService.createBooking(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get booking by ID",
        description = "Retrieve booking details by booking ID. " +
                     "Users can only access their own bookings."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Booking found",
            content = @Content(schema = @Schema(implementation = BookingDTO.class))
        ),
        @ApiResponse(responseCode = "404", description = "Booking not found"),
        @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<BookingDTO> getBooking(
        @Parameter(description = "Booking ID", required = true)
        @PathVariable UUID id
    ) {
        log.info("Fetching booking: {}", id);

        BookingDTO booking = bookingService.getBookingById(id);

        return ResponseEntity.ok(booking);
    }

    @GetMapping("/reference/{reference}")
    @Operation(
        summary = "Get booking by reference",
        description = "Retrieve booking details by booking reference (e.g., BK20251113AB12CD)"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Booking found",
            content = @Content(schema = @Schema(implementation = BookingDTO.class))
        ),
        @ApiResponse(responseCode = "404", description = "Booking not found"),
        @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<BookingDTO> getBookingByReference(
        @Parameter(description = "Booking reference", required = true)
        @PathVariable String reference
    ) {
        log.info("Fetching booking by reference: {}", reference);

        BookingDTO booking = bookingService.getBookingByReference(reference);

        return ResponseEntity.ok(booking);
    }

    @GetMapping
    @Operation(
        summary = "List my bookings",
        description = "Get paginated list of current user's bookings ordered by creation date"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Bookings retrieved successfully"
        )
    })
    public ResponseEntity<Page<BookingDTO>> getMyBookings(
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        log.info("Fetching bookings with pagination: {}", pageable);

        Page<BookingDTO> bookings = bookingService.getMyBookings(pageable);

        return ResponseEntity.ok(bookings);
    }

    @GetMapping("/upcoming")
    @Operation(
        summary = "Get upcoming bookings",
        description = "Get list of confirmed upcoming bookings for current user"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Upcoming bookings retrieved successfully"
        )
    })
    public ResponseEntity<List<BookingDTO>> getUpcomingBookings() {
        log.info("Fetching upcoming bookings");

        List<BookingDTO> bookings = bookingService.getUpcomingBookings();

        return ResponseEntity.ok(bookings);
    }

    @PostMapping("/{id}/cancel")
    @Operation(
        summary = "Cancel a booking",
        description = "Cancel a booking with automatic refund calculation. " +
                     "Refund amount depends on time until departure:\n" +
                     "- Full refund: >24 hours\n" +
                     "- 50% refund: 6-24 hours\n" +
                     "- No refund: <6 hours"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Booking cancelled successfully"),
        @ApiResponse(responseCode = "400", description = "Booking cannot be cancelled"),
        @ApiResponse(responseCode = "404", description = "Booking not found"),
        @ApiResponse(responseCode = "403", description = "Not authorized")
    })
    public ResponseEntity<Void> cancelBooking(
        @Parameter(description = "Booking ID", required = true)
        @PathVariable UUID id,
        @Valid @RequestBody CancelBookingRequest request
    ) {
        log.info("Cancelling booking: {} with reason: {}", id, request.getReason());

        bookingService.cancelBooking(id, request.getReason());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/confirm")
    @Operation(
        summary = "Confirm booking (Internal)",
        description = "Internal endpoint for confirming booking after payment. " +
                     "Called by payment service webhook."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Booking confirmed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status transition"),
        @ApiResponse(responseCode = "404", description = "Booking not found")
    })
    public ResponseEntity<Void> confirmBooking(
        @Parameter(description = "Booking ID", required = true)
        @PathVariable UUID id,
        @Valid @RequestBody ConfirmBookingRequest request
    ) {
        log.info("Confirming booking: {} with payment: {}", id, request.getPaymentId());

        bookingService.confirmBooking(id, request.getPaymentId());

        return ResponseEntity.ok().build();
    }
}

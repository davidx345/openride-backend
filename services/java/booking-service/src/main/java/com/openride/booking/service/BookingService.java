package com.openride.booking.service;

import com.openride.booking.client.DriverServiceClient;
import com.openride.booking.config.BookingConfigProperties;
import com.openride.booking.dto.*;
import com.openride.booking.exception.BookingNotCancellableException;
import com.openride.booking.exception.BookingNotFoundException;
import com.openride.booking.exception.InsufficientSeatsException;
import com.openride.booking.mapper.BookingMapper;
import com.openride.booking.model.Booking;
import com.openride.booking.model.enums.BookingStatus;
import com.openride.booking.repository.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core booking service with distributed locking and state management
 * 
 * Key Features:
 * - Distributed locks for concurrency safety
 * - State machine with validated transitions
 * - Seat hold mechanism with Redis
 * - Payment integration
 * - Cancellation with refund policies
 * - Event publishing for downstream services
 */
@Slf4j
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final SeatAvailabilityService seatAvailabilityService;
    private final SeatHoldService seatHoldService;
    private final DistributedLockService lockService;
    private final DriverServiceClient driverServiceClient;
    private final MatchmakingServiceClient matchmakingClient;
    private final BookingEventPublisher eventPublisher;
    private final BookingMapper bookingMapper;
    private final BookingConfigProperties config;

    public BookingService(
        BookingRepository bookingRepository,
        SeatAvailabilityService seatAvailabilityService,
        SeatHoldService seatHoldService,
        DistributedLockService lockService,
        DriverServiceClient driverServiceClient,
        MatchmakingServiceClient matchmakingClient,
        BookingEventPublisher eventPublisher,
        BookingMapper bookingMapper,
        BookingConfigProperties config
    ) {
        this.bookingRepository = bookingRepository;
        this.seatAvailabilityService = seatAvailabilityService;
        this.seatHoldService = seatHoldService;
        this.lockService = lockService;
        this.driverServiceClient = driverServiceClient;
        this.matchmakingClient = matchmakingClient;
        this.eventPublisher = eventPublisher;
        this.bookingMapper = bookingMapper;
        this.config = config;
    }

    /**
     * Create a new booking with distributed locking
     * 
     * Flow:
     * 1. Validate inputs and check idempotency
     * 2. Acquire distributed lock on route+date
     * 3. Check seat availability
     * 4. Create booking in PENDING status
     * 5. Hold seats in Redis
     * 6. Transition to HELD status
     * 7. Return booking details
     * 
     * @param request Booking creation request
     * @return Booking creation response
     */
    @Transactional
    public CreateBookingResponse createBooking(CreateBookingRequest request) {
        UUID riderId = getCurrentUserId();
        
        // 1. Check idempotency
        if (request.getIdempotencyKey() != null) {
            Optional<Booking> existing = bookingRepository
                .findByIdempotencyKey(request.getIdempotencyKey());
            
            if (existing.isPresent()) {
                log.info("Returning existing booking for idempotency key: {}", 
                    request.getIdempotencyKey());
                return buildCreateResponse(existing.get());
            }
        }

        // 2. Validate route with matchmaking service
        MatchRequest matchRequest = MatchRequest.builder()
            .routeId(request.getRouteId())
            .originStopId(request.getOriginStopId())
            .destinationStopId(request.getDestinationStopId())
            .travelDate(request.getTravelDate())
            .seatsRequired(request.getSeatsBooked())
            .build();
        
        MatchResponse matchResponse = matchmakingClient.requestMatch(matchRequest);
        
        if (!matchResponse.getIsAvailable()) {
            throw new IllegalStateException("Route not available for requested date/seats");
        }
        
        if (matchResponse.getAvailableSeats() < request.getSeatsBooked()) {
            throw new InsufficientSeatsException(
                String.format("Only %d seats available via matchmaking, requested %d",
                    matchResponse.getAvailableSeats(), request.getSeatsBooked())
            );
        }

        // 3. Get route details
        RouteDTO route = driverServiceClient.getRouteById(request.getRouteId());
        validateRouteActive(route);

        // 4. Validate seats requested
        if (request.getSeatsBooked() > config.getMaxSeatsPerBooking()) {
            throw new IllegalArgumentException(
                "Cannot book more than " + config.getMaxSeatsPerBooking() + " seats"
            );
        }

        // 5. Use pricing from matchmaking service
        BigDecimal totalPrice = matchResponse.getTotalPrice();

        // 6. Execute with distributed lock
        String lockKey = lockService.buildRouteLockKey(
            request.getRouteId(),
            request.getTravelDate()
        );

        Booking booking = lockService.executeWithLock(lockKey, () -> {
            return createBookingInternal(request, riderId, route, totalPrice);
        });

        return buildCreateResponse(booking);
    }

    /**
     * Internal booking creation (called within distributed lock)
     */
    private Booking createBookingInternal(
        CreateBookingRequest request,
        UUID riderId,
        RouteDTO route,
        BigDecimal totalPrice
    ) {
        // Check seat availability
        int available = seatAvailabilityService.getAvailableSeatsCount(
            request.getRouteId(),
            request.getTravelDate()
        );

        if (available < request.getSeatsBooked()) {
            throw new InsufficientSeatsException(
                String.format("Only %d seats available, requested %d",
                    available, request.getSeatsBooked())
            );
        }

        // Allocate seats
        List<Integer> allocatedSeats = seatAvailabilityService.allocateSeats(
            request.getRouteId(),
            request.getTravelDate(),
            request.getSeatsBooked()
        );

        // Create booking in PENDING status
        BigDecimal platformFee = calculatePlatformFee(totalPrice);
        BigDecimal pricePerSeat = totalPrice.divide(
            BigDecimal.valueOf(request.getSeatsBooked()),
            2,
            RoundingMode.HALF_UP
        );

        Booking booking = Booking.builder()
            .riderId(riderId)
            .routeId(request.getRouteId())
            .driverId(route.getDriverId())
            .originStopId(request.getOriginStopId())
            .destinationStopId(request.getDestinationStopId())
            .travelDate(request.getTravelDate())
            .departureTime(java.time.LocalTime.parse(route.getDepartureTime()))
            .seatsBooked(request.getSeatsBooked())
            .seatNumbers(allocatedSeats)
            .pricePerSeat(pricePerSeat)
            .totalPrice(totalPrice)
            .platformFee(platformFee)
            .status(BookingStatus.PENDING)
            .idempotencyKey(request.getIdempotencyKey())
            .expiresAt(Instant.now().plusSeconds(config.getHold().getTtlMinutes() * 60L))
            .build();

        booking = bookingRepository.save(booking);

        // Hold seats in Redis
        boolean held = seatHoldService.holdSeats(
            request.getRouteId(),
            request.getTravelDate(),
            allocatedSeats,
            booking.getId()
        );

        if (!held) {
            throw new RuntimeException("Failed to hold seats in Redis");
        }

        // Transition to HELD
        booking.transitionTo(BookingStatus.HELD, "Seats held successfully");
        booking = bookingRepository.save(booking);

        log.info("Created booking {} for rider {} with {} seats on route {}",
            booking.getBookingReference(), riderId, request.getSeatsBooked(),
            request.getRouteId());

        // Publish booking created event
        eventPublisher.publishBookingCreated(booking);

        return booking;
    }

    /**
     * Confirm booking after successful payment
     * 
     * @param bookingId Booking ID
     * @param paymentId Payment ID
     */
    @Transactional
    public void confirmBooking(UUID bookingId, UUID paymentId) {
        // Acquire lock on booking
        String lockKey = lockService.buildBookingLockKey(bookingId);
        
        lockService.executeWithLock(lockKey, () -> {
            Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

            // Validate current status
            if (booking.getStatus() != BookingStatus.HELD && 
                booking.getStatus() != BookingStatus.PAYMENT_INITIATED) {
                log.warn("Booking {} not in holdable state: {}", 
                    bookingId, booking.getStatus());
                return;
            }

            // Update payment info and confirm
            booking.setPaymentId(paymentId);
            booking.setPaymentStatus(com.openride.booking.model.enums.PaymentStatus.SUCCESS);
            booking.transitionTo(BookingStatus.PAID, "Payment confirmed");
            booking.transitionTo(BookingStatus.CONFIRMED, "Booking confirmed");
            booking.setConfirmedAt(Instant.now());
            booking.setExpiresAt(null); // No longer expires

            bookingRepository.save(booking);

            // Release Redis holds (no longer needed)
            seatHoldService.releaseSeats(
                booking.getRouteId(),
                booking.getTravelDate(),
                booking.getSeatNumbers(),
                booking.getId()
            );

            log.info("Confirmed booking {} with payment {}", 
                booking.getBookingReference(), paymentId);

            // Publish booking confirmed event
            eventPublisher.publishBookingConfirmed(booking);
        });
    }

    /**
     * Cancel a booking with refund calculation
     * 
     * @param bookingId Booking ID
     * @param reason Cancellation reason
     */
    @Transactional
    public void cancelBooking(UUID bookingId, String reason) {
        UUID riderId = getCurrentUserId();
        
        String lockKey = lockService.buildBookingLockKey(bookingId);
        
        lockService.executeWithLock(lockKey, () -> {
            Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

            // Authorization check
            if (!booking.getRiderId().equals(riderId)) {
                throw new SecurityException("Not authorized to cancel this booking");
            }

            // Validate can cancel
            if (!booking.isCancellable()) {
                throw new BookingNotCancellableException(
                    "Booking in status " + booking.getStatus() + " cannot be cancelled"
                );
            }

            // Calculate refund
            BigDecimal refundAmount = calculateRefund(booking);

            // Release seats if held
            if (booking.hasSeatsHeld()) {
                seatHoldService.releaseSeats(
                    booking.getRouteId(),
                    booking.getTravelDate(),
                    booking.getSeatNumbers(),
                    booking.getId()
                );
            }

            // Update booking
            booking.transitionTo(BookingStatus.CANCELLED, reason);
            booking.setCancellationReason(reason);
            booking.setCancelledAt(Instant.now());
            booking.setRefundAmount(refundAmount);
            
            if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                booking.setRefundStatus(
                    com.openride.booking.model.enums.RefundStatus.PENDING
                );
            } else {
                booking.setRefundStatus(
                    com.openride.booking.model.enums.RefundStatus.NONE
                );
            }

            bookingRepository.save(booking);

            log.info("Cancelled booking {} with refund amount {}",
                booking.getBookingReference(), refundAmount);

            // Publish booking cancelled event
            eventPublisher.publishBookingCancelled(booking);
        });
    }

    /**
     * Get booking by ID
     * 
     * @param bookingId Booking ID
     * @return Booking DTO
     */
    @Transactional(readOnly = true)
    public BookingDTO getBookingById(UUID bookingId) {
        UUID riderId = getCurrentUserId();
        
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new BookingNotFoundException(bookingId));

        // Authorization check
        if (!booking.getRiderId().equals(riderId)) {
            throw new SecurityException("Not authorized to view this booking");
        }

        return bookingMapper.toDTO(booking);
    }

    /**
     * Get booking by reference
     * 
     * @param bookingReference Booking reference
     * @return Booking DTO
     */
    @Transactional(readOnly = true)
    public BookingDTO getBookingByReference(String bookingReference) {
        UUID riderId = getCurrentUserId();
        
        Booking booking = bookingRepository.findByBookingReference(bookingReference)
            .orElseThrow(() -> new BookingNotFoundException(bookingReference));

        // Authorization check
        if (!booking.getRiderId().equals(riderId)) {
            throw new SecurityException("Not authorized to view this booking");
        }

        return bookingMapper.toDTO(booking);
    }

    /**
     * Get bookings for current user
     * 
     * @param pageable Pagination
     * @return Page of bookings
     */
    @Transactional(readOnly = true)
    public Page<BookingDTO> getMyBookings(Pageable pageable) {
        UUID riderId = getCurrentUserId();
        
        Page<Booking> bookings = bookingRepository
            .findByRiderIdOrderByCreatedAtDesc(riderId, pageable);

        return bookings.map(bookingMapper::toDTO);
    }

    /**
     * Get upcoming bookings for current user
     * 
     * @return List of upcoming bookings
     */
    @Transactional(readOnly = true)
    public List<BookingDTO> getUpcomingBookings() {
        UUID riderId = getCurrentUserId();
        
        List<BookingStatus> activeStatuses = List.of(
            BookingStatus.CONFIRMED,
            BookingStatus.CHECKED_IN
        );

        List<Booking> bookings = bookingRepository.findUpcomingBookingsByRiderId(
            riderId,
            java.time.LocalDate.now(),
            activeStatuses
        );

        return bookings.stream()
            .map(bookingMapper::toDTO)
            .toList();
    }

    // Helper methods

    private void validateRouteActive(RouteDTO route) {
        if (!"ACTIVE".equalsIgnoreCase(route.getStatus())) {
            throw new IllegalArgumentException("Route is not active");
        }
    }

    private BigDecimal calculateTotalPrice(
        UUID routeId,
        UUID originStopId,
        UUID destinationStopId,
        int seats
    ) {
        // Simplified pricing - should integrate with pricing service
        // For now, flat rate of 1000 per seat
        return BigDecimal.valueOf(1000).multiply(BigDecimal.valueOf(seats));
    }

    private BigDecimal calculatePlatformFee(BigDecimal totalPrice) {
        return totalPrice.multiply(config.getPlatformFeePercentage())
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRefund(Booking booking) {
        LocalDateTime departureTime = LocalDateTime.of(
            booking.getTravelDate(),
            booking.getDepartureTime()
        );

        Duration timeUntilDeparture = Duration.between(
            LocalDateTime.now(),
            departureTime
        );

        long hoursUntilDeparture = timeUntilDeparture.toHours();

        // Full refund if > 24 hours
        if (hoursUntilDeparture >= config.getCancellation().getFullRefundHours()) {
            return booking.getTotalPrice();
        }
        
        // Partial refund if 6-24 hours
        if (hoursUntilDeparture >= config.getCancellation().getPartialRefundHours()) {
            return booking.getTotalPrice()
                .multiply(config.getCancellation().getPartialRefundPercentage())
                .setScale(2, RoundingMode.HALF_UP);
        }

        // No refund if < 6 hours
        return BigDecimal.ZERO;
    }

    /**
     * Complete a booking after trip finishes
     * 
     * @param bookingId Booking ID
     */
    @Transactional
    public void completeBooking(UUID bookingId) {
        String lockKey = lockService.buildBookingLockKey(bookingId);
        
        lockService.executeWithLock(lockKey, () -> {
            Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

            // Validate current status
            if (booking.getStatus() != BookingStatus.CHECKED_IN) {
                log.warn("Booking {} not in checked-in state: {}", 
                    bookingId, booking.getStatus());
                return;
            }

            // Transition to completed
            booking.transitionTo(BookingStatus.COMPLETED, "Trip completed successfully");
            booking.setCompletedAt(Instant.now());

            bookingRepository.save(booking);

            log.info("Completed booking {}", booking.getBookingReference());

            // Publish booking completed event
            eventPublisher.publishBookingCompleted(booking);
        });
    }

    private CreateBookingResponse buildCreateResponse(Booking booking) {
        return CreateBookingResponse.builder()
            .booking(bookingMapper.toDTO(booking))
            .expiresAt(booking.getExpiresAt())
            .message("Booking created successfully. Please complete payment.")
            .build();
    }

    private UUID getCurrentUserId() {
        String userId = SecurityContextHolder.getContext()
            .getAuthentication()
            .getName();
        
        return UUID.fromString(userId);
    }
}

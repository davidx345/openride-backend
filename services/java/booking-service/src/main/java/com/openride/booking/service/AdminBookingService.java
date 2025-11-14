package com.openride.booking.service;

import com.openride.booking.dto.AdminBookingFilters;
import com.openride.booking.dto.BookingResponse;
import com.openride.booking.dto.BookingSearchResponse;
import com.openride.booking.exception.BookingNotFoundException;
import com.openride.booking.exception.InvalidStateTransitionException;
import com.openride.booking.model.Booking;
import com.openride.booking.model.enums.BookingStatus;
import com.openride.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for admin booking operations.
 * Provides advanced search, filtering, and management capabilities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminBookingService {

    private final BookingRepository bookingRepository;

    /**
     * Search bookings with advanced filtering and pagination.
     *
     * @param filters search filters
     * @return paginated search results
     */
    @Transactional(readOnly = true)
    public BookingSearchResponse searchBookings(AdminBookingFilters filters) {
        log.debug("Searching bookings with filters: {}", filters);

        // Build dynamic specification
        Specification<Booking> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filters.getRiderId() != null) {
                predicates.add(cb.equal(root.get("riderId"), filters.getRiderId()));
            }

            if (filters.getDriverId() != null) {
                predicates.add(cb.equal(root.get("driverId"), filters.getDriverId()));
            }

            if (filters.getRouteId() != null) {
                predicates.add(cb.equal(root.get("routeId"), filters.getRouteId()));
            }

            if (filters.getStatus() != null) {
                try {
                    BookingStatus status = BookingStatus.valueOf(filters.getStatus().toUpperCase());
                    predicates.add(cb.equal(root.get("status"), status));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid booking status: {}", filters.getStatus());
                }
            }

            if (filters.getPaymentStatus() != null) {
                predicates.add(cb.equal(
                    root.get("paymentStatus"), 
                    filters.getPaymentStatus().toUpperCase()
                ));
            }

            if (filters.getTravelDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get("travelDate"), 
                    filters.getTravelDateFrom()
                ));
            }

            if (filters.getTravelDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get("travelDate"), 
                    filters.getTravelDateTo()
                ));
            }

            if (filters.getBookingReference() != null) {
                predicates.add(cb.like(
                    cb.lower(root.get("bookingReference")), 
                    "%" + filters.getBookingReference().toLowerCase() + "%"
                ));
            }

            if (filters.getCreatedDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get("createdAt"), 
                    filters.getCreatedDateFrom().atStartOfDay()
                ));
            }

            if (filters.getCreatedDateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get("createdAt"), 
                    filters.getCreatedDateTo().plusDays(1).atStartOfDay()
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // Build sort
        Sort sort = Sort.by(
            filters.getSortDirection().equalsIgnoreCase("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC,
            filters.getSortBy() != null ? filters.getSortBy() : "createdAt"
        );

        // Create pageable
        Pageable pageable = PageRequest.of(
            filters.getPage() != null ? filters.getPage() : 0,
            filters.getSize() != null ? filters.getSize() : 20,
            sort
        );

        // Execute query
        Page<Booking> bookingPage = bookingRepository.findAll(spec, pageable);

        // Map to response
        List<BookingResponse> bookingResponses = bookingPage.getContent().stream()
                .map(BookingResponse::fromEntity)
                .collect(Collectors.toList());

        return BookingSearchResponse.builder()
                .bookings(bookingResponses)
                .totalElements(bookingPage.getTotalElements())
                .totalPages(bookingPage.getTotalPages())
                .currentPage(bookingPage.getNumber())
                .pageSize(bookingPage.getSize())
                .hasNext(bookingPage.hasNext())
                .hasPrevious(bookingPage.hasPrevious())
                .build();
    }

    /**
     * Get booking by ID.
     *
     * @param bookingId booking ID
     * @return booking details
     */
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));

        return BookingResponse.fromEntity(booking);
    }

    /**
     * Get booking statistics for a date range.
     *
     * @param dateFrom start date
     * @param dateTo end date
     * @return statistics map
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBookingStatistics(LocalDate dateFrom, LocalDate dateTo) {
        log.info("Calculating booking statistics from {} to {}", dateFrom, dateTo);

        List<Booking> bookings = bookingRepository.findByCreatedAtBetween(
            dateFrom.atStartOfDay(),
            dateTo.plusDays(1).atStartOfDay()
        );

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalBookings", bookings.size());
        stats.put("confirmedBookings", bookings.stream()
            .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
            .count());
        stats.put("cancelledBookings", bookings.stream()
            .filter(b -> b.getStatus() == BookingStatus.CANCELLED)
            .count());
        stats.put("completedBookings", bookings.stream()
            .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
            .count());
        stats.put("totalRevenue", bookings.stream()
            .filter(b -> b.getStatus() == BookingStatus.CONFIRMED || b.getStatus() == BookingStatus.COMPLETED)
            .map(Booking::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
        stats.put("averageBookingValue", bookings.isEmpty() ? BigDecimal.ZERO :
            bookings.stream()
                .map(Booking::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(bookings.size()), 2, BigDecimal.ROUND_HALF_UP));
        stats.put("uniqueRiders", bookings.stream()
            .map(Booking::getRiderId)
            .distinct()
            .count());
        stats.put("uniqueDrivers", bookings.stream()
            .map(Booking::getDriverId)
            .distinct()
            .count());

        return stats;
    }

    /**
     * Force cancel a booking (admin override).
     *
     * @param bookingId booking ID
     * @param reason cancellation reason
     * @return cancelled booking
     */
    @Transactional
    public BookingResponse forceCancelBooking(UUID bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found: " + bookingId));

        // Admin can cancel any non-terminal booking
        if (booking.isTerminal()) {
            throw new InvalidStateTransitionException(
                "Cannot cancel booking in terminal state: " + booking.getStatus()
            );
        }

        // Transition to cancelled
        booking.transitionTo(BookingStatus.CANCELLED, "Admin force cancellation: " + reason);
        booking.setCancellationReason(reason);

        Booking savedBooking = bookingRepository.save(booking);

        log.info("Admin force cancelled booking: {}, reason: {}", bookingId, reason);

        return BookingResponse.fromEntity(savedBooking);
    }
}

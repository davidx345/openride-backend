package com.openride.booking.repository;

import com.openride.booking.model.Booking;
import com.openride.booking.model.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Booking entity with pessimistic locking
 * 
 * Key features:
 * - Pessimistic write locks for concurrency safety
 * - Custom queries for seat inventory
 * - Optimized indexes for performance
 */
@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /**
     * Find booking by ID with pessimistic write lock
     * Used during booking confirmation and cancellation to prevent race conditions
     * 
     * @param id Booking ID
     * @return Optional booking with write lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Find booking by reference with pessimistic write lock
     * 
     * @param bookingReference Booking reference
     * @return Optional booking with write lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.bookingReference = :reference")
    Optional<Booking> findByBookingReferenceForUpdate(@Param("reference") String bookingReference);

    /**
     * Find booking by reference (read-only)
     * 
     * @param bookingReference Booking reference
     * @return Optional booking
     */
    Optional<Booking> findByBookingReference(String bookingReference);

    /**
     * Find booking by idempotency key
     * Used to prevent duplicate bookings
     * 
     * @param idempotencyKey Client-provided idempotency key
     * @return Optional booking
     */
    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find all bookings for a rider
     * 
     * @param riderId Rider ID
     * @param pageable Pagination
     * @return Page of bookings
     */
    Page<Booking> findByRiderIdOrderByCreatedAtDesc(UUID riderId, Pageable pageable);

    /**
     * Find bookings by rider and status
     * 
     * @param riderId Rider ID
     * @param statuses List of statuses
     * @param pageable Pagination
     * @return Page of bookings
     */
    Page<Booking> findByRiderIdAndStatusInOrderByCreatedAtDesc(
        UUID riderId, 
        List<BookingStatus> statuses, 
        Pageable pageable
    );

    /**
     * Find all confirmed bookings for a route on a specific date
     * Used for seat availability calculation
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @param statuses Active statuses (CONFIRMED, CHECKED_IN)
     * @return List of bookings
     */
    @Query("SELECT b FROM Booking b WHERE b.routeId = :routeId " +
           "AND b.travelDate = :travelDate " +
           "AND b.status IN :statuses")
    List<Booking> findByRouteIdAndTravelDateAndStatusIn(
        @Param("routeId") UUID routeId,
        @Param("travelDate") LocalDate travelDate,
        @Param("statuses") List<BookingStatus> statuses
    );

    /**
     * Count active bookings for a route on a specific date
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @param statuses Active statuses
     * @return Count of bookings
     */
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.routeId = :routeId " +
           "AND b.travelDate = :travelDate " +
           "AND b.status IN :statuses")
    long countByRouteIdAndTravelDateAndStatusIn(
        @Param("routeId") UUID routeId,
        @Param("travelDate") LocalDate travelDate,
        @Param("statuses") List<BookingStatus> statuses
    );

    /**
     * Calculate total seats booked for a route on a specific date
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @param statuses Active statuses
     * @return Total seats booked
     */
    @Query("SELECT COALESCE(SUM(b.seatsBooked), 0) FROM Booking b " +
           "WHERE b.routeId = :routeId " +
           "AND b.travelDate = :travelDate " +
           "AND b.status IN :statuses")
    int sumSeatsBookedByRouteIdAndTravelDateAndStatusIn(
        @Param("routeId") UUID routeId,
        @Param("travelDate") LocalDate travelDate,
        @Param("statuses") List<BookingStatus> statuses
    );

    /**
     * Find expired bookings that need cleanup
     * 
     * @param statuses Statuses to check (PENDING, HELD)
     * @param expiryThreshold Expiry threshold
     * @return List of expired bookings
     */
    @Query("SELECT b FROM Booking b WHERE b.status IN :statuses " +
           "AND b.expiresAt IS NOT NULL " +
           "AND b.expiresAt < :threshold")
    List<Booking> findByStatusInAndExpiresAtBefore(
        @Param("statuses") List<BookingStatus> statuses,
        @Param("threshold") Instant expiryThreshold
    );

    /**
     * Find bookings by payment ID
     * 
     * @param paymentId Payment ID
     * @return Optional booking
     */
    Optional<Booking> findByPaymentId(UUID paymentId);

    /**
     * Find upcoming bookings for a rider
     * 
     * @param riderId Rider ID
     * @param fromDate From date
     * @param statuses Active statuses
     * @return List of bookings
     */
    @Query("SELECT b FROM Booking b WHERE b.riderId = :riderId " +
           "AND b.travelDate >= :fromDate " +
           "AND b.status IN :statuses " +
           "ORDER BY b.travelDate ASC, b.departureTime ASC")
    List<Booking> findUpcomingBookingsByRiderId(
        @Param("riderId") UUID riderId,
        @Param("fromDate") LocalDate fromDate,
        @Param("statuses") List<BookingStatus> statuses
    );

    /**
     * Find all bookings for a driver on a specific date
     * 
     * @param driverId Driver ID
     * @param travelDate Travel date
     * @param statuses Active statuses
     * @return List of bookings
     */
    @Query("SELECT b FROM Booking b WHERE b.driverId = :driverId " +
           "AND b.travelDate = :travelDate " +
           "AND b.status IN :statuses " +
           "ORDER BY b.originStopId")
    List<Booking> findByDriverIdAndTravelDateAndStatusIn(
        @Param("driverId") UUID driverId,
        @Param("travelDate") LocalDate travelDate,
        @Param("statuses") List<BookingStatus> statuses
    );

    /**
     * Check if a booking exists for specific criteria (duplicate check)
     * 
     * @param riderId Rider ID
     * @param routeId Route ID
     * @param travelDate Travel date
     * @param statuses Active statuses
     * @return True if booking exists
     */
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END " +
           "FROM Booking b WHERE b.riderId = :riderId " +
           "AND b.routeId = :routeId " +
           "AND b.travelDate = :travelDate " +
           "AND b.status IN :statuses")
    boolean existsByRiderIdAndRouteIdAndTravelDateAndStatusIn(
        @Param("riderId") UUID riderId,
        @Param("routeId") UUID routeId,
        @Param("travelDate") LocalDate travelDate,
        @Param("statuses") List<BookingStatus> statuses
    );
}

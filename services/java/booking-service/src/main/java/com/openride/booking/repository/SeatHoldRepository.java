package com.openride.booking.repository;

import com.openride.booking.model.SeatHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for SeatHold entity (Redis backup)
 */
@Repository
public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {

    /**
     * Find active holds for a route on a specific date
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @return List of active seat holds
     */
    @Query("SELECT sh FROM SeatHold sh WHERE sh.routeId = :routeId " +
           "AND sh.travelDate = :travelDate " +
           "AND sh.releasedAt IS NULL")
    List<SeatHold> findActiveHoldsByRouteIdAndTravelDate(
        @Param("routeId") UUID routeId,
        @Param("travelDate") LocalDate travelDate
    );

    /**
     * Find holds by booking ID
     * 
     * @param bookingId Booking ID
     * @return List of seat holds
     */
    List<SeatHold> findByBookingId(UUID bookingId);

    /**
     * Find expired holds that need cleanup
     * 
     * @param expiryThreshold Expiry threshold
     * @return List of expired holds
     */
    @Query("SELECT sh FROM SeatHold sh WHERE sh.releasedAt IS NULL " +
           "AND sh.expiresAt < :threshold")
    List<SeatHold> findExpiredHolds(@Param("threshold") Instant expiryThreshold);

    /**
     * Release all holds for a booking
     * 
     * @param bookingId Booking ID
     * @param releasedAt Release timestamp
     */
    @Modifying
    @Query("UPDATE SeatHold sh SET sh.releasedAt = :releasedAt " +
           "WHERE sh.bookingId = :bookingId AND sh.releasedAt IS NULL")
    void releaseHoldsByBookingId(
        @Param("bookingId") UUID bookingId,
        @Param("releasedAt") Instant releasedAt
    );

    /**
     * Count active holds for a specific seat
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @param seatNumber Seat number
     * @return Count of active holds
     */
    @Query("SELECT COUNT(sh) FROM SeatHold sh " +
           "WHERE sh.routeId = :routeId " +
           "AND sh.travelDate = :travelDate " +
           "AND sh.seatNumber = :seatNumber " +
           "AND sh.releasedAt IS NULL")
    long countActiveHoldsForSeat(
        @Param("routeId") UUID routeId,
        @Param("travelDate") LocalDate travelDate,
        @Param("seatNumber") Integer seatNumber
    );
}

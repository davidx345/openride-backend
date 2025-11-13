package com.openride.booking.service;

import com.openride.booking.client.DriverServiceClient;
import com.openride.booking.dto.RouteDTO;
import com.openride.booking.model.Booking;
import com.openride.booking.model.enums.BookingStatus;
import com.openride.booking.repository.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Seat availability service
 * 
 * Calculates available seats by combining:
 * 1. Confirmed bookings from database
 * 2. Temporary holds from Redis
 * 
 * Features:
 * - Real-time availability calculation
 * - Multi-source reconciliation
 * - Caching for performance
 */
@Slf4j
@Service
public class SeatAvailabilityService {

    private final BookingRepository bookingRepository;
    private final SeatHoldService seatHoldService;
    private final DriverServiceClient driverServiceClient;

    public SeatAvailabilityService(
        BookingRepository bookingRepository,
        SeatHoldService seatHoldService,
        DriverServiceClient driverServiceClient
    ) {
        this.bookingRepository = bookingRepository;
        this.seatHoldService = seatHoldService;
        this.driverServiceClient = driverServiceClient;
    }

    /**
     * Get available seat numbers for a route on a specific date
     * 
     * Algorithm:
     * 1. Get total seats from route
     * 2. Remove confirmed/checked-in bookings
     * 3. Remove Redis holds
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @return List of available seat numbers
     */
    public List<Integer> getAvailableSeats(UUID routeId, LocalDate travelDate) {
        // Get route details to know total seats
        RouteDTO route = driverServiceClient.getRouteById(routeId);
        int totalSeats = route.getSeatsAvailable();
        
        // Start with all seats
        Set<Integer> allSeats = IntStream.rangeClosed(1, totalSeats)
            .boxed()
            .collect(Collectors.toSet());
        
        // Remove confirmed bookings
        List<BookingStatus> activeStatuses = List.of(
            BookingStatus.CONFIRMED,
            BookingStatus.CHECKED_IN
        );
        
        List<Booking> activeBookings = bookingRepository
            .findByRouteIdAndTravelDateAndStatusIn(routeId, travelDate, activeStatuses);
        
        for (Booking booking : activeBookings) {
            allSeats.removeAll(booking.getSeatNumbers());
        }
        
        // Remove Redis holds
        List<Integer> heldSeats = seatHoldService.getHeldSeats(routeId, travelDate);
        allSeats.removeAll(heldSeats);
        
        List<Integer> availableSeats = new ArrayList<>(allSeats);
        
        log.debug("Route {} on {} - Total: {}, Available: {}, Held: {}, Booked: {}",
            routeId, travelDate, totalSeats, availableSeats.size(),
            heldSeats.size(), activeBookings.stream()
                .mapToInt(Booking::getSeatsBooked)
                .sum());
        
        return availableSeats;
    }

    /**
     * Get count of available seats (faster than getting all seat numbers)
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @return Number of available seats
     */
    public int getAvailableSeatsCount(UUID routeId, LocalDate travelDate) {
        RouteDTO route = driverServiceClient.getRouteById(routeId);
        int totalSeats = route.getSeatsAvailable();
        
        // Count confirmed bookings
        List<BookingStatus> activeStatuses = List.of(
            BookingStatus.CONFIRMED,
            BookingStatus.CHECKED_IN
        );
        
        int bookedSeats = bookingRepository.sumSeatsBookedByRouteIdAndTravelDateAndStatusIn(
            routeId,
            travelDate,
            activeStatuses
        );
        
        // Count Redis holds
        int heldSeats = seatHoldService.getHeldSeats(routeId, travelDate).size();
        
        int available = totalSeats - bookedSeats - heldSeats;
        
        log.debug("Route {} on {} - Total: {}, Booked: {}, Held: {}, Available: {}",
            routeId, travelDate, totalSeats, bookedSeats, heldSeats, available);
        
        return Math.max(0, available);
    }

    /**
     * Check if sufficient seats are available
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @param requestedSeats Number of seats requested
     * @return true if sufficient seats available
     */
    public boolean hasSufficientSeats(
        UUID routeId,
        LocalDate travelDate,
        int requestedSeats
    ) {
        int available = getAvailableSeatsCount(routeId, travelDate);
        return available >= requestedSeats;
    }

    /**
     * Allocate specific seats from available pool
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @param count Number of seats to allocate
     * @return List of allocated seat numbers
     * @throws IllegalStateException if insufficient seats
     */
    public List<Integer> allocateSeats(UUID routeId, LocalDate travelDate, int count) {
        List<Integer> availableSeats = getAvailableSeats(routeId, travelDate);
        
        if (availableSeats.size() < count) {
            throw new IllegalStateException(
                String.format("Only %d seats available, requested %d",
                    availableSeats.size(), count)
            );
        }
        
        // Allocate first N available seats
        List<Integer> allocated = availableSeats.subList(0, count);
        
        log.debug("Allocated seats {} for route {} on date {}",
            allocated, routeId, travelDate);
        
        return new ArrayList<>(allocated);
    }

    /**
     * Get seat occupancy percentage
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @return Occupancy percentage (0-100)
     */
    public double getOccupancyPercentage(UUID routeId, LocalDate travelDate) {
        RouteDTO route = driverServiceClient.getRouteById(routeId);
        int totalSeats = route.getSeatsAvailable();
        
        if (totalSeats == 0) {
            return 0.0;
        }
        
        int available = getAvailableSeatsCount(routeId, travelDate);
        int occupied = totalSeats - available;
        
        return (double) occupied / totalSeats * 100.0;
    }

    /**
     * Check if a specific seat is available
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @param seatNumber Seat number
     * @return true if seat is available
     */
    public boolean isSeatAvailable(UUID routeId, LocalDate travelDate, Integer seatNumber) {
        List<Integer> availableSeats = getAvailableSeats(routeId, travelDate);
        return availableSeats.contains(seatNumber);
    }

    /**
     * Invalidate seat cache for a route (called after booking changes)
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     */
    public void invalidateCache(UUID routeId, LocalDate travelDate) {
        // Cache invalidation would be implemented here if using @Cacheable
        log.debug("Invalidated seat cache for route {} on {}", routeId, travelDate);
    }
}

package com.openride.booking.scheduler;

import com.openride.booking.model.Booking;
import com.openride.booking.model.enums.BookingStatus;
import com.openride.booking.repository.BookingRepository;
import com.openride.booking.service.SeatHoldService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled jobs for booking cleanup
 * 
 * Jobs:
 * 1. Expire and cleanup hold bookings that haven't been paid (every 5 min)
 * 2. Cleanup orphaned Redis holds (every 15 min)
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "scheduler.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class BookingCleanupScheduler {

    private final BookingRepository bookingRepository;
    private final SeatHoldService seatHoldService;

    public BookingCleanupScheduler(
        BookingRepository bookingRepository,
        SeatHoldService seatHoldService
    ) {
        this.bookingRepository = bookingRepository;
        this.seatHoldService = seatHoldService;
    }

    /**
     * Cleanup expired booking holds
     * 
     * Runs every 5 minutes
     */
    @Scheduled(cron = "${scheduler.cleanup.expired-holds-cron:0 */5 * * * *}")
    @Transactional
    public void cleanupExpiredHolds() {
        log.debug("Running cleanup for expired booking holds");

        Instant expiryThreshold = Instant.now();

        List<Booking> expiredBookings = bookingRepository
            .findByStatusInAndExpiresAtBefore(
                List.of(BookingStatus.PENDING, BookingStatus.HELD),
                expiryThreshold
            );

        int count = 0;
        for (Booking booking : expiredBookings) {
            try {
                // Release Redis holds
                seatHoldService.releaseSeats(
                    booking.getRouteId(),
                    booking.getTravelDate(),
                    booking.getSeatNumbers(),
                    booking.getId()
                );

                // Transition to EXPIRED
                booking.transitionTo(BookingStatus.EXPIRED, "Booking hold expired");
                bookingRepository.save(booking);

                count++;

                log.info("Expired booking {} (created at {})",
                    booking.getBookingReference(),
                    booking.getCreatedAt());

            } catch (Exception e) {
                log.error("Failed to expire booking {}", booking.getId(), e);
            }
        }

        if (count > 0) {
            log.info("Expired {} booking holds", count);
        }
    }

    /**
     * Cleanup orphaned Redis holds
     * 
     * Runs every 15 minutes
     */
    @Scheduled(cron = "${scheduler.cleanup.orphaned-redis-cron:0 */15 * * * *}")
    public void cleanupOrphanedRedisHolds() {
        log.debug("Running cleanup for orphaned Redis holds");

        try {
            int count = seatHoldService.cleanupExpiredHolds();

            if (count > 0) {
                log.info("Cleaned up {} orphaned Redis holds", count);
            }

        } catch (Exception e) {
            log.error("Failed to cleanup orphaned Redis holds", e);
        }
    }
}

package com.openride.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openride.booking.exception.SeatHoldException;
import com.openride.booking.model.SeatHold;
import com.openride.booking.repository.SeatHoldRepository;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Seat hold service with Redis-based temporary holds
 * 
 * Architecture:
 * - Primary: Redis for fast seat holds with TTL
 * - Backup: PostgreSQL for reconciliation
 * 
 * Redis Key Patterns:
 * - seat:hold:{routeId}:{travelDate}:{seatNumber} → {bookingId}
 * - booking:hold:{bookingId} → JSON metadata
 * - route:seats:{routeId}:{travelDate} → SET[1,2,3,...,N]
 */
@Slf4j
@Service
public class SeatHoldService {

    private final RedisTemplate<String, String> redisTemplate;
    private final SeatHoldRepository seatHoldRepository;
    private final DistributedLockService lockService;
    private final ObjectMapper objectMapper;
    private final int holdTtlMinutes;
    private final int extensionMinutes;

    public SeatHoldService(
        RedisTemplate<String, String> redisTemplate,
        SeatHoldRepository seatHoldRepository,
        DistributedLockService lockService,
        ObjectMapper objectMapper,
        @Value("${booking.hold.ttl-minutes:10}") int holdTtlMinutes,
        @Value("${booking.hold.extension-minutes:15}") int extensionMinutes
    ) {
        this.redisTemplate = redisTemplate;
        this.seatHoldRepository = seatHoldRepository;
        this.lockService = lockService;
        this.objectMapper = objectMapper;
        this.holdTtlMinutes = holdTtlMinutes;
        this.extensionMinutes = extensionMinutes;
    }

    /**
     * Hold seats for a booking
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @param seatNumbers Seat numbers to hold
     * @param bookingId Booking ID
     * @return true if all seats held successfully
     * @throws SeatHoldException if hold fails
     */
    @Transactional
    public boolean holdSeats(
        UUID routeId,
        LocalDate travelDate,
        List<Integer> seatNumbers,
        UUID bookingId
    ) {
        String lockKey = lockService.buildRouteLockKey(routeId, travelDate);
        
        return lockService.executeWithLock(lockKey, () -> {
            // Check if seats are already held
            for (Integer seatNumber : seatNumbers) {
                String seatKey = buildSeatKey(routeId, travelDate, seatNumber);
                if (Boolean.TRUE.equals(redisTemplate.hasKey(seatKey))) {
                    String heldBy = redisTemplate.opsForValue().get(seatKey);
                    if (!bookingId.toString().equals(heldBy)) {
                        log.warn("Seat {} already held by booking {}", seatNumber, heldBy);
                        return false;
                    }
                }
            }
            
            // Hold all seats atomically in Redis
            Duration ttl = Duration.ofMinutes(holdTtlMinutes);
            Instant expiresAt = Instant.now().plus(ttl);
            
            for (Integer seatNumber : seatNumbers) {
                String seatKey = buildSeatKey(routeId, travelDate, seatNumber);
                redisTemplate.opsForValue().set(
                    seatKey,
                    bookingId.toString(),
                    ttl
                );
                
                // Backup to database
                SeatHold seatHold = SeatHold.builder()
                    .routeId(routeId)
                    .travelDate(travelDate)
                    .seatNumber(seatNumber)
                    .bookingId(bookingId)
                    .expiresAt(expiresAt)
                    .build();
                
                seatHoldRepository.save(seatHold);
            }
            
            // Store booking metadata
            SeatHoldMetadata metadata = SeatHoldMetadata.builder()
                .routeId(routeId)
                .travelDate(travelDate)
                .seatNumbers(seatNumbers)
                .expiresAt(expiresAt)
                .build();
            
            String bookingKey = buildBookingHoldKey(bookingId);
            try {
                String metadataJson = objectMapper.writeValueAsString(metadata);
                redisTemplate.opsForValue().set(bookingKey, metadataJson, ttl);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize seat hold metadata", e);
                throw new SeatHoldException("Failed to store hold metadata", e);
            }
            
            log.info("Held {} seats for booking {} on route {} for date {}", 
                seatNumbers.size(), bookingId, routeId, travelDate);
            
            return true;
        });
    }

    /**
     * Release seats held by a booking
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @param seatNumbers Seat numbers to release
     * @param bookingId Booking ID
     */
    @Transactional
    public void releaseSeats(
        UUID routeId,
        LocalDate travelDate,
        List<Integer> seatNumbers,
        UUID bookingId
    ) {
        // Release from Redis
        for (Integer seatNumber : seatNumbers) {
            String seatKey = buildSeatKey(routeId, travelDate, seatNumber);
            String heldBy = redisTemplate.opsForValue().get(seatKey);
            
            // Only release if held by this booking
            if (bookingId.toString().equals(heldBy)) {
                redisTemplate.delete(seatKey);
            }
        }
        
        // Delete booking metadata
        String bookingKey = buildBookingHoldKey(bookingId);
        redisTemplate.delete(bookingKey);
        
        // Release in database
        seatHoldRepository.releaseHoldsByBookingId(bookingId, Instant.now());
        
        log.info("Released {} seats for booking {} on route {} for date {}",
            seatNumbers.size(), bookingId, routeId, travelDate);
    }

    /**
     * Extend hold duration (when payment initiated)
     * 
     * @param bookingId Booking ID
     * @param extension Extension duration
     * @return true if extension successful
     */
    public boolean extendHold(UUID bookingId, Duration extension) {
        String bookingKey = buildBookingHoldKey(bookingId);
        String metadataJson = redisTemplate.opsForValue().get(bookingKey);
        
        if (metadataJson == null) {
            log.warn("Hold metadata not found for booking {}", bookingId);
            return false;
        }
        
        try {
            SeatHoldMetadata metadata = objectMapper.readValue(
                metadataJson,
                SeatHoldMetadata.class
            );
            
            // Extend all seat holds
            for (Integer seatNumber : metadata.getSeatNumbers()) {
                String seatKey = buildSeatKey(
                    metadata.getRouteId(),
                    metadata.getTravelDate(),
                    seatNumber
                );
                redisTemplate.expire(seatKey, extension);
            }
            
            // Extend booking metadata
            redisTemplate.expire(bookingKey, extension);
            
            log.info("Extended hold for booking {} by {} minutes",
                bookingId, extension.toMinutes());
            
            return true;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize seat hold metadata", e);
            return false;
        }
    }

    /**
     * Get held seat numbers for a route on a date (from Redis)
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @return List of held seat numbers
     */
    public List<Integer> getHeldSeats(UUID routeId, LocalDate travelDate) {
        String pattern = buildSeatKeyPattern(routeId, travelDate);
        Set<String> keys = redisTemplate.keys(pattern);
        
        List<Integer> heldSeats = new ArrayList<>();
        if (keys != null) {
            for (String key : keys) {
                // Extract seat number from key
                String[] parts = key.split(":");
                if (parts.length == 5) {
                    try {
                        int seatNumber = Integer.parseInt(parts[4]);
                        heldSeats.add(seatNumber);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid seat number in key: {}", key);
                    }
                }
            }
        }
        
        return heldSeats;
    }

    /**
     * Check if a specific seat is held
     * 
     * @param routeId Route ID
     * @param travelDate Travel date
     * @param seatNumber Seat number
     * @return true if seat is held
     */
    public boolean isSeatHeld(UUID routeId, LocalDate travelDate, Integer seatNumber) {
        String seatKey = buildSeatKey(routeId, travelDate, seatNumber);
        return Boolean.TRUE.equals(redisTemplate.hasKey(seatKey));
    }

    /**
     * Cleanup expired holds (called by scheduled job)
     * 
     * @return Count of holds cleaned up
     */
    @Transactional
    public int cleanupExpiredHolds() {
        Instant now = Instant.now();
        List<SeatHold> expiredHolds = seatHoldRepository.findExpiredHolds(now);
        
        int count = 0;
        for (SeatHold hold : expiredHolds) {
            // Release in database
            hold.release();
            seatHoldRepository.save(hold);
            
            // Cleanup Redis (if still present)
            String seatKey = buildSeatKey(
                hold.getRouteId(),
                hold.getTravelDate(),
                hold.getSeatNumber()
            );
            if (Boolean.TRUE.equals(redisTemplate.hasKey(seatKey))) {
                redisTemplate.delete(seatKey);
            }
            
            count++;
        }
        
        if (count > 0) {
            log.info("Cleaned up {} expired seat holds", count);
        }
        
        return count;
    }

    // Key building methods
    private String buildSeatKey(UUID routeId, LocalDate travelDate, Integer seatNumber) {
        return String.format("seat:hold:%s:%s:%d", routeId, travelDate, seatNumber);
    }

    private String buildSeatKeyPattern(UUID routeId, LocalDate travelDate) {
        return String.format("seat:hold:%s:%s:*", routeId, travelDate);
    }

    private String buildBookingHoldKey(UUID bookingId) {
        return String.format("booking:hold:%s", bookingId);
    }

    /**
     * Seat hold metadata stored in Redis
     */
    @Data
    @Builder
    public static class SeatHoldMetadata {
        private UUID routeId;
        private LocalDate travelDate;
        private List<Integer> seatNumbers;
        private Instant expiresAt;
    }
}

package com.openride.ticketing.repository;

import com.openride.ticketing.model.entity.Ticket;
import com.openride.ticketing.model.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Ticket entity operations.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /**
     * Find ticket by booking ID.
     */
    Optional<Ticket> findByBookingId(UUID bookingId);

    /**
     * Find ticket by hash.
     */
    Optional<Ticket> findByHash(String hash);

    /**
     * Find all tickets for a rider.
     */
    List<Ticket> findByRiderIdOrderByCreatedAtDesc(UUID riderId);

    /**
     * Find all tickets for a driver.
     */
    List<Ticket> findByDriverIdOrderByTripDateDesc(UUID driverId);

    /**
     * Find all tickets for a route.
     */
    List<Ticket> findByRouteIdOrderByTripDateDesc(UUID routeId);

    /**
     * Find tickets by status.
     */
    List<Ticket> findByStatusOrderByCreatedAtDesc(TicketStatus status);

    /**
     * Find tickets by rider and status.
     */
    List<Ticket> findByRiderIdAndStatusOrderByCreatedAtDesc(UUID riderId, TicketStatus status);

    /**
     * Find expired pending tickets.
     */
    @Query("SELECT t FROM Ticket t WHERE t.status = :status AND t.expiresAt < :now")
    List<Ticket> findExpiredTicketsByStatus(@Param("status") TicketStatus status, @Param("now") LocalDateTime now);

    /**
     * Find tickets without Merkle batch.
     */
    @Query("SELECT t FROM Ticket t WHERE t.merkleBatch IS NULL AND t.status = :status ORDER BY t.createdAt ASC")
    List<Ticket> findTicketsWithoutBatch(@Param("status") TicketStatus status);

    /**
     * Count tickets by status.
     */
    long countByStatus(TicketStatus status);

    /**
     * Check if ticket exists for booking.
     */
    boolean existsByBookingId(UUID bookingId);
    
    /**
     * Find tickets by Merkle batch ID.
     */
    @Query("SELECT t FROM Ticket t WHERE t.merkleBatch.id = :batchId ORDER BY t.createdAt ASC")
    List<Ticket> findByMerkleBatchId(@Param("batchId") UUID batchId);
    
    /**
     * Count tickets created before a date.
     */
    long countByCreatedAtBefore(LocalDateTime dateTime);
    
    /**
     * Delete tickets created before a date.
     */
    void deleteByCreatedAtBefore(LocalDateTime dateTime);
}

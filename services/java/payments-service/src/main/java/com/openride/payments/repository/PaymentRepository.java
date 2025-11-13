package com.openride.payments.repository;

import com.openride.payments.model.Payment;
import com.openride.payments.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Payment entity operations.
 * Provides data access methods with optimized queries.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Finds payment by korapay reference.
     */
    Optional<Payment> findByKorapayReference(String korapayReference);

    /**
     * Finds payment by korapay transaction ID.
     */
    Optional<Payment> findByKorapayTransactionId(String korapayTransactionId);

    /**
     * Finds payment by idempotency key.
     * Used to prevent duplicate payment requests.
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Finds payment by booking ID with pessimistic lock.
     * Prevents concurrent payment attempts for same booking.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.bookingId = :bookingId")
    Optional<Payment> findByBookingIdWithLock(@Param("bookingId") UUID bookingId);

    /**
     * Finds payment by booking ID.
     */
    Optional<Payment> findByBookingId(UUID bookingId);

    /**
     * Finds all payments for a rider, ordered by creation date descending.
     */
    @Query("SELECT p FROM Payment p WHERE p.riderId = :riderId ORDER BY p.createdAt DESC")
    List<Payment> findByRiderIdOrderByCreatedAtDesc(@Param("riderId") UUID riderId);

    /**
     * Finds all payments for a rider with specific status.
     */
    @Query("SELECT p FROM Payment p WHERE p.riderId = :riderId AND p.status = :status ORDER BY p.createdAt DESC")
    List<Payment> findByRiderIdAndStatus(@Param("riderId") UUID riderId, @Param("status") PaymentStatus status);

    /**
     * Finds all expired pending payments that need cleanup.
     */
    @Query("SELECT p FROM Payment p WHERE p.status = 'PENDING' AND p.expiresAt < :now")
    List<Payment> findExpiredPendingPayments(@Param("now") LocalDateTime now);

    /**
     * Finds all payments created within a date range.
     * Used for reconciliation.
     */
    @Query("SELECT p FROM Payment p WHERE p.createdAt >= :startDate AND p.createdAt < :endDate ORDER BY p.createdAt")
    List<Payment> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Finds all successful payments within a date range.
     * Used for reconciliation.
     */
    @Query("SELECT p FROM Payment p WHERE p.status = 'SUCCESS' AND p.completedAt >= :startDate AND p.completedAt < :endDate ORDER BY p.completedAt")
    List<Payment> findSuccessfulPaymentsBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Finds all payments by status and confirmed timestamp range.
     * Used for reconciliation.
     */
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.completedAt >= :startDate AND p.completedAt < :endDate ORDER BY p.completedAt")
    List<Payment> findByStatusAndConfirmedAtBetween(
        @Param("status") PaymentStatus status,
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Checks if a payment exists for a booking.
     */
    boolean existsByBookingId(UUID bookingId);

    /**
     * Counts payments by status.
     */
    long countByStatus(PaymentStatus status);
}
